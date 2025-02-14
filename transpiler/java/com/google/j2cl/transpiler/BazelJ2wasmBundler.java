/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.j2cl.transpiler;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
import com.google.j2cl.common.Problems;
import com.google.j2cl.common.Problems.FatalError;
import com.google.j2cl.common.SourcePosition;
import com.google.j2cl.common.bazel.BazelWorker;
import com.google.j2cl.common.bazel.FileCache;
import com.google.j2cl.transpiler.ast.AstUtils;
import com.google.j2cl.transpiler.ast.CompilationUnit;
import com.google.j2cl.transpiler.ast.Library;
import com.google.j2cl.transpiler.ast.Method;
import com.google.j2cl.transpiler.ast.MethodDescriptor;
import com.google.j2cl.transpiler.ast.MethodDescriptor.MethodOrigin;
import com.google.j2cl.transpiler.ast.ReturnStatement;
import com.google.j2cl.transpiler.ast.StringLiteral;
import com.google.j2cl.transpiler.ast.StringLiteralGettersCreator;
import com.google.j2cl.transpiler.ast.TypeDeclaration;
import com.google.j2cl.transpiler.ast.TypeDeclaration.Kind;
import com.google.j2cl.transpiler.ast.TypeDescriptors;
import com.google.j2cl.transpiler.backend.wasm.JsImportsGenerator;
import com.google.j2cl.transpiler.backend.wasm.SharedSnippet;
import com.google.j2cl.transpiler.backend.wasm.StringLiteralInfo;
import com.google.j2cl.transpiler.backend.wasm.Summary;
import com.google.j2cl.transpiler.backend.wasm.TypeInfo;
import com.google.j2cl.transpiler.backend.wasm.WasmConstructsGenerator;
import com.google.j2cl.transpiler.backend.wasm.WasmGeneratorStage;
import com.google.j2cl.transpiler.frontend.jdt.JdtEnvironment;
import com.google.j2cl.transpiler.frontend.jdt.JdtParser;
import com.google.j2cl.transpiler.passes.RewriteReferenceEqualityOperations;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.MapOptionHandler;

/** Runs The J2wasmBundler as a worker. */
final class BazelJ2wasmBundler extends BazelWorker {

  private static final int CACHE_SIZE =
      Integer.parseInt(System.getProperty("j2cl.bundler.cachesize", "5000"));

  private static final FileCache<String> moduleContentsCache =
      new FileCache<>(BazelJ2wasmBundler::readModule, CACHE_SIZE);

  private static final FileCache<Summary> summaryCache =
      new FileCache<>(BazelJ2wasmBundler::readSummary, CACHE_SIZE);

  @Argument(required = true, usage = "The list of modular output directories", multiValued = true)
  List<String> inputs = null;

  @Option(
      name = "-output",
      required = true,
      metaVar = "<path>",
      usage = "Directory or zip into which to place the bundled output.")
  Path output;

  @Option(
      name = "-jsimports",
      required = true,
      metaVar = "<path>",
      usage = "Directory or zip into which to place the JavaScript imports output.")
  Path jsimportPath;

  @Option(
      name = "-classpath",
      required = true,
      metaVar = "<path>",
      usage = "Specifies where to find all the class files for the application.")
  String classPath;

  @Option(name = "-define", handler = MapOptionHandler.class, hidden = true)
  Map<String, String> defines = new HashMap<>();

  @Override
  protected void run(Problems problems) {
    createBundle(problems);
  }

  private void createBundle(Problems problems) {
    emitModuleFile(problems);
    emitJsImportsFile(problems);
  }

  private void emitModuleFile(Problems problems) {
    var typeGraph = new TypeGraph(getSummaries());

    // Create an environment to initialize the well known type descriptors to be able to synthesize
    // code.
    // TODO(b/294284380): consider removing JDT and manually synthesizing required types.
    var classPathEntries = Splitter.on(File.pathSeparatorChar).splitToList(this.classPath);
    new JdtEnvironment(
        new JdtParser(classPathEntries, problems), TypeDescriptors.getWellKnownTypeNames());

    var referencedPropertyKeys =
        getSummaries().flatMap(s -> s.getPropertyKeysList().stream()).collect(toImmutableSet());

    // Synthesize globals and methods for string literals.
    synthesizeStringLiteralGetters(referencedPropertyKeys);

    var generatorStage = new WasmGeneratorStage(library, problems);

    Stream<String> literalGetterMethods =
        compilationUnit.getTypes().stream()
            .flatMap(t -> t.getMethods().stream())
            .map(m -> generatorStage.emitToString(g -> g.renderMethod(m)));

    String literalGlobals = generatorStage.emitToString(g -> g.emitGlobals(library));

    ImmutableList<String> moduleContents =
        Streams.concat(
                Stream.of("(module (rec"),
                getModuleParts("types"),
                streamDedupedValues(Summary::getTypeSnippetsList),
                Stream.of(typeGraph.getTopLevelItableStructDeclaration()),
                typeGraph.getClasses().stream().map(TypeGraph.Type::getItableStructDeclaration),
                Stream.of(")"),
                getModuleParts("data"),
                getModuleParts("globals"),
                streamDedupedValues(Summary::getGlobalSnippetsList),
                Stream.of(typeGraph.getEmptyItableStructDeclaration()),
                typeGraph.getClasses().stream().map(TypeGraph.Type::getItableInitialization),
                Stream.of(literalGlobals),
                streamDedupedValues(Summary::getWasmImportSnippetsList),
                Stream.of(generatorStage.emitToString(WasmConstructsGenerator::emitExceptionTag)),
                getModuleParts("functions"),
                literalGetterMethods,
                Stream.of(")"))
            .collect(toImmutableList());

    writeToFile(output.toString(), moduleContents, problems);
  }

  private Stream<String> streamDedupedValues(
      Function<Summary, Collection<SharedSnippet>> snippetGetter) {
    return getDedupedSnippets(snippetGetter).values().stream();
  }

  private ImmutableMap<String, String> getDedupedSnippets(
      Function<Summary, Collection<SharedSnippet>> snippetGetter) {
    return getSummaries()
        .flatMap(s -> snippetGetter.apply(s).stream())
        .collect(toImmutableMap(SharedSnippet::getKey, SharedSnippet::getSnippet, (i1, i2) -> i1));
  }

  private void synthesizeStringLiteralGetters(Set<String> referencedPropertyKeys) {

    var stringLiteralHolder =
        new com.google.j2cl.transpiler.ast.Type(
            SourcePosition.NONE,
            TypeDeclaration.newBuilder()
                .setClassComponents(
                    ImmutableList.of("wasm", "stringLiteral", "StringLiteralHolder"))
                .setKind(Kind.CLASS)
                .build());

    compilationUnit.addType(stringLiteralHolder);

    var stringLiteralGetterCreator = new StringLiteralGettersCreator();

    // Synthesize the getters and forwarding methods for the string literals in the code.
    getSummaries()
        .flatMap(s -> s.getStringLiteralsList().stream())
        .forEach(
            s ->
                // Get descriptor for the getter and synthesize the method logic if it is the
                // first time it was found.
                synthesizeStringLiteralGetter(stringLiteralHolder, stringLiteralGetterCreator, s));

    // Synthesize the getters and forwarding methods for the string literals that are values of
    // system properties.
    referencedPropertyKeys.forEach(
        pk -> {
          var value = defines.get(pk);
          MethodDescriptor systemGetPropertyGetter = AstUtils.getSystemGetPropertyGetter(pk);
          if (value == null) {
            // Synthesize a getter that returns null.
            synthesizeAbsentPropertyMethod(systemGetPropertyGetter);
          } else {
            synthesizeStringLiteralGetter(
                stringLiteralHolder,
                stringLiteralGetterCreator,
                StringLiteralInfo.newBuilder()
                    .setContent(value)
                    .setMethodName(systemGetPropertyGetter.getName())
                    .setEnclosingTypeName(
                        systemGetPropertyGetter
                            .getEnclosingTypeDescriptor()
                            .getQualifiedSourceName())
                    .build());
          }
        });

    // Perform the rewriting on the newly synthesized string literal getters.
    new RewriteReferenceEqualityOperations().applyTo(compilationUnit);
  }

  private void synthesizeStringLiteralGetter(
      com.google.j2cl.transpiler.ast.Type stringLiteralHolder,
      StringLiteralGettersCreator stringLiteralGetterCreator,
      StringLiteralInfo s) {
    MethodDescriptor m =
        stringLiteralGetterCreator.getOrCreateLiteralMethod(
            stringLiteralHolder, new StringLiteral(s.getContent()), /* synthesizeMethod= */ true);

    // Synthesize the forwarding logic.
    String qualifiedBinaryTypeName = s.getEnclosingTypeName();
    TypeDeclaration typeDeclaration =
        TypeDeclaration.newBuilder()
            .setClassComponents(Arrays.asList(qualifiedBinaryTypeName.split("\\.")))
            .setKind(Kind.CLASS)
            .build();
    com.google.j2cl.transpiler.ast.Type type = getType(typeDeclaration);

    Method forwarderMethod = synthesizeForwardingMethod(m, typeDeclaration, s.getMethodName());
    type.addMember(forwarderMethod);
  }

  private static Method synthesizeForwardingMethod(
      MethodDescriptor literalGetter, TypeDeclaration fromType, String forwardingMethodName) {
    MethodDescriptor forwarderDescriptor =
        MethodDescriptor.newBuilder()
            .setEnclosingTypeDescriptor(fromType.toUnparameterizedTypeDescriptor())
            .setName(forwardingMethodName)
            .setOrigin(MethodOrigin.SYNTHETIC_STRING_LITERAL_GETTER)
            .setStatic(true)
            .setReturnTypeDescriptor(TypeDescriptors.get().javaLangString)
            .build();
    return AstUtils.createForwardingMethod(
        SourcePosition.NONE,
        null,
        forwarderDescriptor,
        literalGetter,
        /* jsDocDescription= */ null);
  }

  /** Synthesizes a method that returns null to implement absent properties. */
  private void synthesizeAbsentPropertyMethod(MethodDescriptor propertyGetter) {
    var typeDeclaration = propertyGetter.getEnclosingTypeDescriptor().getTypeDeclaration();
    com.google.j2cl.transpiler.ast.Type type = getType(typeDeclaration);
    type.addMember(
        Method.newBuilder()
            .setMethodDescriptor(propertyGetter)
            .setSourcePosition(SourcePosition.NONE)
            .setStatements(
                ReturnStatement.newBuilder()
                    .setExpression(TypeDescriptors.get().javaLangString.getNullValue())
                    .setSourcePosition(SourcePosition.NONE)
                    .build())
            .build());
  }

  /** Synthetic compilation unit for all types synthesized at bundling time. */
  private final CompilationUnit compilationUnit = CompilationUnit.createSynthetic("j2wasm-bundler");

  /** Synthetic library all types synthesized at bundling time. */
  private final Library library =
      Library.newBuilder().setCompilationUnits(ImmutableList.of(compilationUnit)).build();

  private final Map<TypeDeclaration, com.google.j2cl.transpiler.ast.Type> typesByDeclaration =
      new LinkedHashMap<>();

  private com.google.j2cl.transpiler.ast.Type getType(TypeDeclaration typeDeclaration) {
    return typesByDeclaration.computeIfAbsent(
        typeDeclaration,
        t -> {
          var newType = new com.google.j2cl.transpiler.ast.Type(SourcePosition.NONE, t);
          compilationUnit.addType(newType);
          return newType;
        });
  }

  /** Represents the inheritance structure of the whole application. */
  private static class TypeGraph {

    private static final int NO_TYPE_INDEX = 0;
    // The list of all interfaces.
    private final List<TypeGraph.Type> classes = new ArrayList<>();
    // The list of all classes.
    private final List<TypeGraph.Type> interfaces = new ArrayList<>();
    private final Map<String, TypeGraph.Type> typesByName = new LinkedHashMap<>();

    private TypeGraph(Stream<Summary> summaries) {
      // Collect all types from all summaries.
      summaries.forEachOrdered(this::addToTypeGraph);
    }

    private void addToTypeGraph(Summary summary) {
      for (int interfaceId : summary.getInterfacesList()) {
        var interfaceName = summary.getTypeNames(interfaceId);
        var interfaceType = new TypeGraph.Type(interfaceName);
        typesByName.put(interfaceName, interfaceType);
        interfaces.add(interfaceType);
      }

      for (TypeInfo typeInfo : summary.getTypesList()) {
        var name = summary.getTypeNames(typeInfo.getTypeId());
        var type =
            typesByName.computeIfAbsent(name, n -> new TypeGraph.Type(n, typeInfo.getAbstract()));
        classes.add(type);
        if (typeInfo.getExtendsType() != NO_TYPE_INDEX) {
          String superTypeName = summary.getTypeNames(typeInfo.getExtendsType());
          type.superType = checkNotNull(typesByName.get(superTypeName));
        }
        for (int interfaceId : typeInfo.getImplementsTypesList()) {
          String interfaceName = summary.getTypeNames(interfaceId);
          var interfaceType = typesByName.get(interfaceName);
          type.implementedInterfaces.add(interfaceType);
        }
      }
    }

    List<TypeGraph.Type> getClasses() {
      return classes;
    }

    /** Emits the top-level itable struct. */
    String getTopLevelItableStructDeclaration() {
      StringBuilder sb = new StringBuilder();
      sb.append("(type $itable (sub (struct\n");
      // In the unoptimized itables each interface has its own slot.
      for (TypeGraph.Type i : interfaces) {
        sb.append(format("  (field %s (ref null struct))\n", i.name));
      }
      sb.append(")))\n");
      return sb.toString();
    }

    public String getEmptyItableStructDeclaration() {
      StringBuilder sb = new StringBuilder();
      sb.append("(global $itable.empty (ref $itable) (struct.new $itable \n");

      for (TypeGraph.Type unused : interfaces) {
        sb.append("(ref.null struct)\n");
      }
      sb.append("))\n");
      return sb.toString();
    }

    private class Type {
      private final String name;
      private Type superType;
      private final Set<Type> implementedInterfaces = new HashSet<>();
      private final boolean isAbstract;

      public Type(String name, boolean isAbstract) {
        this.name = name;
        this.isAbstract = isAbstract;
      }

      public Type(String name) {
        this(name, false);
      }

      /** Emits the itable struct type for a class. */
      public String getItableStructDeclaration() {
        StringBuilder sb = new StringBuilder();
        sb.append(
            format(
                "(type %s.itable (sub %s (struct \n",
                name, superType != null ? superType.name + ".itable" : "$itable"));

        for (var i : interfaces) {
          sb.append(
              format(
                  "  (field %s (ref %s))\n",
                  i.name, implementsInterface(i) ? i.name + ".vtable" : "null struct"));
        }
        sb.append(")))\n");
        return sb.toString();
      }

      public String getItableInitialization() {
        if (isAbstract) {
          // Abstract classes don't have itable instances.
          return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(
            format("(global %s.itable (ref %s.itable) (struct.new %s.itable \n", name, name, name));

        for (Type ifce : interfaces) {
          if (implementsInterface(ifce)) {
            sb.append(format("(global.get %s.vtable@%s)\n", ifce.name, name));
          } else {
            sb.append("(ref.null struct)\n");
          }
        }
        sb.append("))\n");
        return sb.toString();
      }

      private boolean implementsInterface(Type type) {
        return implementedInterfaces.contains(type)
            || (superType != null && superType.implementsInterface(type));
      }
    }
  }

  private void emitJsImportsFile(Problems problems) {
    var requiredModules =
        getSummaries()
            .flatMap(s -> s.getJsImportRequiresList().stream())
            .distinct()
            .collect(toImmutableList());

    var jsImportsContents = getDedupedSnippets(Summary::getJsImportSnippetsList);

    writeToFile(
        jsimportPath.toString(),
        ImmutableList.of(JsImportsGenerator.generateOutputs(requiredModules, jsImportsContents)),
        problems);
  }

  private Stream<Summary> getSummaries() {
    return inputs.stream()
        .map(d -> format("%s/summary.binpb", d))
        .filter(n -> new File(n).exists())
        .map(summaryCache::get);
  }

  private Stream<String> getModuleParts(String name) {
    return inputs.stream()
        .map(d -> format("%s/%s.wat", d, name))
        .filter(n -> new File(n).exists())
        .map(BazelJ2wasmBundler.moduleContentsCache::get);
  }

  private static Summary readSummary(Path summaryPath) throws IOException {
    try (InputStream inputStream = java.nio.file.Files.newInputStream(summaryPath)) {
      return Summary.parseFrom(inputStream);
    }
  }

  private static String readModule(Path modulePath) throws IOException {
    return java.nio.file.Files.readString(modulePath);
  }

  private static void writeToFile(String filePath, List<String> contents, Problems problems) {
    try {
      Files.asCharSink(new File(filePath), UTF_8).writeLines(contents);
    } catch (IOException e) {
      problems.fatal(FatalError.CANNOT_WRITE_FILE, e.toString());
    }
  }

  public static void main(String[] workerArgs) throws Exception {
    BazelWorker.start(workerArgs, BazelJ2wasmBundler::new);
  }
}
