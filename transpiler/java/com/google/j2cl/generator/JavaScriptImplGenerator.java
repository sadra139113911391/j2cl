/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.j2cl.generator;

import com.google.common.base.Preconditions;
import com.google.j2cl.ast.AstUtils;
import com.google.j2cl.ast.Block;
import com.google.j2cl.ast.Field;
import com.google.j2cl.ast.JavaType;
import com.google.j2cl.ast.Method;
import com.google.j2cl.ast.Statement;
import com.google.j2cl.ast.TypeDescriptor;
import com.google.j2cl.ast.TypeDescriptors;
import com.google.j2cl.ast.TypeDescriptors.BootstrapType;
import com.google.j2cl.ast.Visibility;
import com.google.j2cl.errors.Errors;
import com.google.j2cl.generator.visitors.Import;
import com.google.j2cl.generator.visitors.ImportGatheringVisitor.ImportCategory;
import com.google.j2cl.generator.visitors.ImportUtils;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Generates JavaScript source impl files.
 */
public class JavaScriptImplGenerator extends JavaScriptGenerator {
  private VelocityContext velocityContext;
  private String nativeSource;
  private String relativeSourceMapLocation;
  private SourceBuilder sb;
  private boolean subclassesJsConstructorClass;

  public JavaScriptImplGenerator(Errors errors, JavaType javaType, VelocityEngine velocityEngine) {
    super(errors, javaType, velocityEngine);
    velocityContext = createContext();
    this.subclassesJsConstructorClass = javaType.getDescriptor().subclassesJsConstructorClass();
  }

  @Override
  public String getTemplateFilePath() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getSuffix() {
    return ".impl.js";
  }

  public void setRelativeSourceMapLocation(String relativeSourceMapLocation) {
    Preconditions.checkArgument(relativeSourceMapLocation != null);
    this.relativeSourceMapLocation = relativeSourceMapLocation;
  }

  public void setNativeSource(String nativeSource) {
    Preconditions.checkArgument(nativeSource != null);
    this.nativeSource = nativeSource;
  }

  private String renderTemplate(String templatePath) {
    StringWriter outputBuffer = new StringWriter();
    boolean success =
        velocityEngine.mergeTemplate(
            templatePath, StandardCharsets.UTF_8.name(), velocityContext, outputBuffer);
    if (!success) {
      errors.error(Errors.Error.ERR_CANNOT_GENERATE_OUTPUT);
      return "";
    }
    return outputBuffer.toString();
  }

  @Override
  public String toSource() {
    sb = new SourceBuilder();
    renderFileOverview();
    renderImports();
    renderTypeAnnotation();
    renderTypeBody();
    renderStaticFieldDeclarations();
    renderClassLiteralFieldDeclaration();
    renderMarkImplementorCalls();
    renderNativeSource();
    renderExports();
    renderSourceMapLocation();
    return sb.build();
  }

  private void renderFileOverview() {
    String transpiledFrom = javaType.getDescriptor().getRawTypeDescriptor().getBinaryName();
    sb.appendln("/**");
    sb.appendln(" * @fileoverview Impl transpiled from " + transpiledFrom + ".");
    sb.appendln(" *");
    sb.appendln(
        " * @suppress {suspiciousCode, transitionalSuspiciousCodeWarnings, uselessCode, const}");
    sb.appendln(" */");
  }

  private void renderImports() {
    TypeDescriptor selfTypeDescriptor = javaType.getDescriptor().getRawTypeDescriptor();
    Import selfImport = new Import(selfTypeDescriptor.getSimpleName(), selfTypeDescriptor);

    // goog.module(...) declaration.
    sb.appendln("goog.module('%s');", selfImport.getImplModulePath());
    sb.newLine();
    sb.newLine();

    // goog.require(...) for eager imports.
    for (Import eagerImport : ImportUtils.sortedList(importsByCategory.get(ImportCategory.EAGER))) {
      String alias = eagerImport.getAlias();
      String path = eagerImport.getImplModulePath();
      sb.appendln("let %s = goog.require('%s');", alias, path);
    }
    sb.newLine();

    // goog.forwardDeclare(...) for lazy imports.
    for (Import lazyImport : ImportUtils.sortedList(importsByCategory.get(ImportCategory.LAZY))) {
      String alias = lazyImport.getAlias();
      String path = lazyImport.getImplModulePath();
      sb.appendln("let %s = goog.forwardDeclare('%s');", alias, path);
    }
    sb.newLine();
    sb.newLine();
  }

  private void renderTypeAnnotation() {
    if (javaType.isJsOverlayImpl()) {
      // Do nothing.
    } else if (javaType.isInterface()) {
      sb.append(renderTemplate("com/google/j2cl/generator/JsInterfaceAnnotation.vm"));
    } else { // Not an interface so it is a Class.
      sb.append(renderTemplate("com/google/j2cl/generator/JsClassAnnotation.vm"));
    }
  }

  private void renderTypeBody() {
    String className = sourceGenerator.toSource(javaType.getDescriptor());
    String extendsClause = GeneratorUtils.getExtendsClause(javaType, sourceGenerator);
    sb.appendln("class %s %s{", className, extendsClause);
    if (!javaType.isJsOverlayImpl() && !javaType.isInterface()) {
      renderConstructor();
    }
    renderJavaTypeMethods();
    renderMarkImplementorMethod();
    renderIsInstanceMethod();
    renderIsAssignableFromMethod();
    renderCopyMethod();
    renderGetClassMethod();
    renderStaticFieldGettersSetters();
    renderClinit();
    renderInit();
    sb.appendln("};");
    sb.newLine();
    sb.newLine();
  }

  private void renderJavaTypeMethods() {
    for (Method method : javaType.getMethods()) {
      velocityContext.put("method", method);
      if (method.isConstructor()) {
        renderConstructorFactoryMethod(method);
      } else {
        if (GeneratorUtils.shouldNotEmitCode(method)) {
          continue;
        }
        if (method.getDescriptor().isJsOverlay() && !method.getDescriptor().isStatic()) {
          continue;
        }
        sb.appendln("/**");
        if (method.isSynthetic()) {
          sb.appendln(" * Synthetic method.");
        }
        if (method.isAbstract()) {
          sb.appendln(" * Abstract method.");
        }
        if (method.isOverride()) {
          sb.appendln(" * @override");
        }
        if (!method.getDescriptor().getTypeParameterTypeDescriptors().isEmpty()) {
          String templateParamNames =
              sourceGenerator.getJsDocNames(
                  method.getDescriptor().getTypeParameterTypeDescriptors());
          sb.appendln(" * @template %s", templateParamNames);
        }
        for (String paramTypeName :
            GeneratorUtils.getParameterAnnotationsJsDoc(method, sourceGenerator)) {
          sb.appendln(" * @param %s", paramTypeName);
        }
        if (!GeneratorUtils.isVoid(method.getDescriptor().getReturnTypeDescriptor())) {
          String returnTypeName =
              sourceGenerator.getJsDocName(method.getDescriptor().getReturnTypeDescriptor());
          sb.appendln(" * @return {%s}", returnTypeName);
        }
        sb.appendln(" * @%s", method.getDescriptor().getVisibility().jsText);
        sb.appendln(" */");
        if (method.isNative()) {
          sb.appendln("  // native " + GeneratorUtils.getMethodHeader(method, sourceGenerator));
        } else {
          sb.appendln(GeneratorUtils.getMethodHeader(method, sourceGenerator));
          StatementTransformer.transform(method.getBody(), environment, sb);
        }
        sb.newLine();
      }
    }
  }

  private void renderMarkImplementorMethod() {
    if (!javaType.isInterface() || javaType.isJsOverlayImpl()) {
      return; // Only render markImplementor code for interfaces.
    }
    String mangledTypeName = ManglingNameUtils.getMangledName(javaType.getDescriptor());

    sb.appendln("/**");
    sb.appendln(" * Marks the provided class as implementing this interface.");
    sb.appendln(" * @param {Function} classConstructor");
    sb.appendln(" * @public");
    sb.appendln(" */");
    sb.appendln("static $markImplementor(classConstructor) {");
    for (TypeDescriptor superInterface : javaType.getSuperInterfaceTypeDescriptors()) {
      if (superInterface.isNative()) {
        continue;
      }
      String superInterfaceName = sourceGenerator.toSource(superInterface);
      sb.appendln("%s.$markImplementor(classConstructor);", superInterfaceName);
    }
    sb.appendln("/**");
    sb.appendln(" * @public {boolean}");
    sb.appendln(" */");
    sb.appendln("classConstructor.prototype.$implements__%s = true;", mangledTypeName);
    sb.appendln("}");
    sb.newLine();
  }

  private void renderConstructorFactoryMethod(Method method) {
    if (subclassesJsConstructorClass) {
      velocityContext.put("isPrimaryConstructor", !AstUtils.hasThisCall(method));
      sb.append(renderTemplate("com/google/j2cl/generator/JsPrimaryConstructorFactoryMethods.vm"));
    } else {
      sb.append(renderTemplate("com/google/j2cl/generator/JsConstructorFactoryMethods.vm"));
    }
  }


  //TODO: Move this to the ast in a normalization pass.
  private void renderIsInstanceMethod() {
    if (javaType.isJsOverlayImpl()) {
      renderIsInstanceForJsOverlayType();
    } else if (javaType.isInterface()) {
      renderIsInstanceForInterfaceType();
    } else { // Not an interface so it is a Class.
      renderIsInstanceForClassType();
    }
    sb.newLine();
  }


  private void renderIsInstanceForClassType() {
    String mangledTypeName = ManglingNameUtils.getMangledName(javaType.getDescriptor());
    if (GeneratorUtils.superBoxedTypeAsPrimitive(javaType.getDescriptor())) {
      sb.appendln("/**");
      sb.appendln(" * $isInstance() function implementation is provided separately.");
      sb.appendln(" */");
      return;
    }
    sb.appendln("/**");
    sb.appendln(" * Returns whether the provided instance is an instance of this class.");
    sb.appendln(" * @return {boolean}");
    sb.appendln(" * @public");
    sb.appendln(" */");
    sb.appendln("static $isInstance(instance) {");
    if (javaType.getDescriptor().isJsFunctionImplementation()) {
      sb.appendln("return instance != null && instance.$is__%s;", mangledTypeName);
    } else {
      String className = sourceGenerator.toSource(javaType.getDescriptor());
      sb.appendln("return instance instanceof %s;", className);
    }
    sb.appendln("}");
  }

  private void renderIsInstanceForInterfaceType() {
    sb.appendln("/**");
    sb.appendln(" * Returns whether the provided instance is of a class that implements this");
    sb.appendln(" * interface.");
    sb.appendln(" * @return {boolean}");
    sb.appendln(" * @public");
    sb.appendln(" */");
    sb.appendln("static $isInstance(instance) {");
    if (javaType.getDescriptor().isJsFunctionInterface()) {
      sb.appendln("return instance != null && typeof instance == \"function\";");
    } else {
      String mangledTypeName = ManglingNameUtils.getMangledName(javaType.getDescriptor());
      sb.appendln("return instance != null && instance.$implements__%s;", mangledTypeName);
    }
    sb.appendln("}");
  }

  private void renderIsInstanceForJsOverlayType() {
    sb.appendln("/**");
    sb.appendln(" * Returns whether the provided instance is an instance of this class.");
    sb.appendln(" * @return {boolean}");
    sb.appendln(" * @public");
    sb.appendln(" */");
    if (javaType.isInterface()) {
      sb.appendln("static $isInstance(instance) { return true; }");
    } else {
      String nativeClassName =
          sourceGenerator.toSource(javaType.getNativeTypeDescriptor().getRawTypeDescriptor());
      sb.appendln(
          "static $isInstance(instance) { return instance instanceof %s; }", nativeClassName);
    }
  }

  //TODO: Move this to the ast in a normalization pass.
  private void renderIsAssignableFromMethod() {
    String mangledTypeName = ManglingNameUtils.getMangledName(javaType.getDescriptor());
    String className = sourceGenerator.toSource(javaType.getDescriptor());
    if (javaType.isJsOverlayImpl()) {
      return; // Don't render for overlay types.
    }
    sb.appendln("/**");
    sb.appendln(" * Returns whether the provided class is or extends this class.");
    sb.appendln(" * @param {Function} classConstructor");
    sb.appendln(" * @return {boolean}");
    sb.appendln(" * @public");
    sb.appendln(" */");
    sb.appendln("static $isAssignableFrom(classConstructor) {");
    if (javaType.isInterface()) { // For interfaces
      sb.appendln(
          "return classConstructor != null && classConstructor.prototype.$implements__%s;",
          mangledTypeName);
    } else { // For classes
      BootstrapType.NATIVE_UTIL.getDescriptor();
      String utilAlias = sourceGenerator.toSource(BootstrapType.NATIVE_UTIL.getDescriptor());
      sb.appendln("return %s.$canCastClass(classConstructor, %s);", utilAlias, className);
    }
    sb.appendln("}");
    sb.newLine();
  }

  //TODO: Move this to the ast in a normalization pass.
  //TODO: may copy Objects methods (equals, hashCode, etc. ) as well.
  private void renderCopyMethod() {
    String className = sourceGenerator.toSource(javaType.getDescriptor());
    String mangledTypeName = ManglingNameUtils.getMangledName(javaType.getDescriptor());
    if (!javaType.getDescriptor().isJsFunctionImplementation()) {
      return; // Only render the $copy method for jsfunctions
    }
    sb.appendln("/**");
    sb.appendln(" * Copies the fields from {@code from} to {@code to}.");
    sb.appendln(" * @param {%s} from", className);
    sb.appendln(" * @param {*} to");
    sb.appendln(" * @public");
    sb.appendln(" */");
    sb.appendln("static $copy(from, to) {");
    for (Field field : javaType.getInstanceFields()) {
      String fieldName = ManglingNameUtils.getMangledName(field.getDescriptor());
      sb.appendln("to.%s = from.%s;", fieldName, fieldName);
    }
    sb.appendln("// Marks the object is an instance of this class.");
    sb.appendln("to.$is__%s = true;", mangledTypeName);
    sb.appendln("}");
    sb.newLine();
  }

  //TODO: Move this to the ast in a normalization pass.
  private void renderGetClassMethod() {
    if (javaType.isJsOverlayImpl()) {
      return; // Don't render $getClass for overlay types.
    }
    String classAlias =
        sourceGenerator.toSource(TypeDescriptors.get().javaLangClass.getRawTypeDescriptor());
    String className = sourceGenerator.toSource(javaType.getDescriptor());
    String utilAlias = sourceGenerator.toSource(BootstrapType.NATIVE_UTIL.getDescriptor());
    sb.appendln("/**");
    sb.appendln(" * @return {%s}", classAlias);
    sb.appendln(" * @public", classAlias);
    sb.appendln(" */");
    sb.appendln("static $getClass() {");
    sb.appendln(className + ".$clinit();");
    sb.appendln("if (!%s.$class%s_) {", className, className);
    if (javaType.isInterface()) {
      sb.appendln("%s.$class%s_ = %s.$createForInterface(", className, className, classAlias);
    } else if (javaType.isEnum()) {
      sb.appendln("%s.$class%s_ = %s.$createForEnum(", className, className, classAlias);
    } else {
      sb.appendln("%s.$class%s_ = %s.$createForClass(", className, className, classAlias);
    }
    sb.appendln("%s.$generateId('%s'),", utilAlias, javaType.getDescriptor().getSimpleName());
    sb.appendln("%s.$generateId('%s'),", utilAlias, javaType.getDescriptor().getBinaryName());
    sb.append(String.format(
        "%s.$generateId('%s')", utilAlias, javaType.getDescriptor().getSourceName()));
    if (javaType.isEnum()) {
      sb.appendln(",null);");
    } else {
      sb.appendln(");");
    }
    sb.appendln("}");
    sb.appendln("return %s.$class%s_;", className, className);
    sb.appendln("}");
    sb.newLine();

    if (!javaType.isJsOverlayImpl() && !javaType.isInterface()) {
      sb.appendln("/**");
      if (!javaType.getSuperTypeDescriptor().isNative()) {
        sb.appendln(" * @override");
      }
      sb.appendln(" * @return {%s}", classAlias);
      sb.appendln(" * @public");
      sb.appendln(" */");
      sb.appendln("m_getClass() { return %s.$getClass(); }", className);
      sb.newLine();
    }
  }

  private void renderConstructor() {
    if (!subclassesJsConstructorClass) {
      sb.append(renderTemplate("com/google/j2cl/generator/JsConstructor.vm"));
    } else {
      velocityContext.put("primaryConstructor", AstUtils.getPrimaryConstructor(javaType));
      sb.append(renderTemplate("com/google/j2cl/generator/JsPrimaryConstructor.vm"));
    }
  }

  private void renderStaticFieldGettersSetters() {
    String className = sourceGenerator.toSource(javaType.getDescriptor());
    for (Field staticField : javaType.getStaticFields()) {
      Visibility staticFieldVisibility = staticField.getDescriptor().getVisibility();
      String staticFieldType =
          sourceGenerator.getJsDocName(staticField.getDescriptor().getTypeDescriptor());
      String indirectStaticFieldName =
          ManglingNameUtils.getMangledName(staticField.getDescriptor());
      String directStaticFieldAccess =
          ManglingNameUtils.getMangledName(staticField.getDescriptor(), true);

      sb.appendln("/**");
      sb.appendln(" * A static field getter.");
      sb.appendln(" * @return {%s}", staticFieldType);
      sb.appendln(" * @%s", staticFieldVisibility.jsText);
      sb.appendln(" */");
      sb.appendln("static get %s() {", indirectStaticFieldName);
      sb.appendln("%s.$clinit();", className);
      sb.appendln("return %s.%s;", className, directStaticFieldAccess);
      sb.appendln("}");
      sb.newLine();

      sb.appendln("/**");
      sb.appendln(" * A static field setter.");
      sb.appendln(" * @param {%s} value", staticFieldType);
      sb.appendln(" * @return {void}", staticFieldType);
      sb.appendln(" * @%s", staticFieldVisibility.jsText);
      sb.appendln(" */");
      sb.appendln("static set %s(value) {", indirectStaticFieldName);
      sb.appendln("%s.$clinit();", className);
      sb.appendln("%s.%s = value;", className, directStaticFieldAccess);
      sb.appendln("}");
      sb.newLine();
    }
  }

  // TODO: Move this to the ast in a normalization pass.
  private void renderClinit() {
    List<Import> lazyImports = ImportUtils.sortedList(importsByCategory.get(ImportCategory.LAZY));
    if (!GeneratorUtils.needClinit(javaType, lazyImports)) {
      return;
    }
    String className = sourceGenerator.toSource(javaType.getDescriptor());
    sb.appendln("/**");
    sb.appendln(" * Runs inline static field initializers.");
    sb.appendln(" * @public");
    sb.appendln(" */");
    sourceGenerator.setClinitEnclosingTypeDescriptor(javaType.getDescriptor());
    sb.appendln("static $clinit() {");
    if (GeneratorUtils.needRewriteClinit(javaType)) {
      // Set this method to reference an empty function so that it cannot be called again.
      sb.appendln("%s.$clinit = function() {};", className);
    }
    // goog.module.get(...) for lazy imports.
    for (Import lazyImport : ImportUtils.sortedList(importsByCategory.get(ImportCategory.LAZY))) {
      String alias = lazyImport.getAlias();
      String path = lazyImport.getImplModulePath();
      sb.appendln("%s = goog.module.get('%s');", alias, path);
    }
    if (GeneratorUtils.needCallSuperClinit(javaType)) {
      // call the super class $clinit.
      TypeDescriptor superTypeDescriptor = javaType.getSuperTypeDescriptor();
      String superTypeName = sourceGenerator.toSource(superTypeDescriptor);
      sb.appendln("%s.$clinit();", superTypeName);
    }
    for (Field field : javaType.getStaticFields()) {
      if (field.hasInitializer() && !field.isCompileTimeConstant()) {
        String fieldInitializer = sourceGenerator.toSource(field.getInitializer());
        String fieldName = ManglingNameUtils.getMangledName(field.getDescriptor());
        sb.appendln("%s.$%s = %s;", className, fieldName, fieldInitializer);
      }
    }
    for (Block block : javaType.getStaticInitializerBlocks()) {
      for (Statement initializer : block.getStatements()) {
        StatementTransformer.transform(initializer, environment, sb);
      }
    }
    sb.appendln("}");
    sb.newLine();
    sourceGenerator.setClinitEnclosingTypeDescriptor(null);
  }

  //TODO: Move this to the ast in a normalization pass.
  private void renderInit() {
    if (javaType.isJsOverlayImpl() || javaType.isInterface()) {
      return;
    }
    sb.appendln("/**");
    sb.appendln(" * Runs instance field and block initializers.");
    sb.appendln(" * @private");
    sb.appendln(" */");
    String mangledTypeName = ManglingNameUtils.getMangledName(javaType.getDescriptor());
    String methodName = String.format("$init__%s", mangledTypeName);
    sb.appendln("%s() {", methodName);
    for (Field field : javaType.getInstanceFields()) {
      if (field.hasInitializer() && !field.isCompileTimeConstant()) {
        String fieldInitializer = sourceGenerator.toSource(field.getInitializer());
        String fieldName = ManglingNameUtils.getMangledName(field.getDescriptor());
        sb.appendln("this.%s = %s;", fieldName, fieldInitializer);
      }
    }
    for (Block block : javaType.getInstanceInitializerBlocks()) {
      for (Statement initializer : block.getStatements()) {
        StatementTransformer.transform(initializer, environment, sb);
      }
    }
    sb.appendln("}");
  }

  private void renderStaticFieldDeclarations() {
    String className = sourceGenerator.toSource(javaType.getDescriptor());
    for (Field staticField : javaType.getStaticFields()) {
      String jsDocType =
          sourceGenerator.getJsDocName(staticField.getDescriptor().getTypeDescriptor());
      String directFieldAccess =
          ManglingNameUtils.getMangledName(staticField.getDescriptor(), true);
      String initialValue = sourceGenerator.toSource(GeneratorUtils.getInitialValue(staticField));
      sb.appendln("/**");
      sb.appendln(" * @private {%s}", jsDocType);
      sb.appendln(" */");
      sb.appendln("%s.%s = %s;", className, directFieldAccess, initialValue);
      sb.newLine();
      sb.newLine();
    }
  }

  private void renderClassLiteralFieldDeclaration() {
    String className = sourceGenerator.toSource(javaType.getDescriptor());
    String classAlias =
        sourceGenerator.toSource(TypeDescriptors.get().javaLangClass.getRawTypeDescriptor());
    if (javaType.isJsOverlayImpl()) {
      return;
    }
    sb.appendln("/**");
    sb.appendln(" * The class literal field.");
    sb.appendln(" * @private {%s}", classAlias);
    sb.appendln(" */");
    sb.appendln("%s.$class%s_ = null;", className, className);
    sb.newLine();
    sb.newLine();
  }

  /**
   * Here we call markImplementor on all interfaces such that the class can be queried at run time
   * to determine if it implements an interface.
   */
  private void renderMarkImplementorCalls() {
    String className = sourceGenerator.toSource(javaType.getDescriptor());
    if (javaType.isJsOverlayImpl()) {
      return; // Do nothing
    }
    if (javaType.isInterface()) {
      // TODO: remove cast after b/20102666 is handled in Closure.
      sb.appendln("%s.$markImplementor(/** @type {Function} */ (%s));", className, className);
    } else { // Not an interface so it is a Class.
      for (TypeDescriptor interfaceTypeDescriptor : javaType.getSuperInterfaceTypeDescriptors()) {
        if (interfaceTypeDescriptor.isNative()) {
          continue;
        }
        String interfaceName = sourceGenerator.toSource(interfaceTypeDescriptor);
        sb.appendln("%s.$markImplementor(%s);", interfaceName, className);
      }
    }
    sb.newLine();
    sb.newLine();
  }

  private void renderNativeSource() {
    if (nativeSource != null) {
      sb.appendln("/**");
      sb.appendln(" * Native Method Injection");
      sb.appendln(" */");
      sb.appendln(nativeSource);
      sb.newLine();
    }
  }

  private void renderExports() {
    sb.appendln("/**");
    sb.appendln(" * Export class.");
    sb.appendln(" */");
    String className = sourceGenerator.toSource(javaType.getDescriptor());
    sb.appendln("exports = %s;", className);
  }

  private void renderSourceMapLocation() {
    if (relativeSourceMapLocation != null) {
      sb.append(String.format("//# sourceMappingURL=%s", relativeSourceMapLocation));
    }
  }
}
