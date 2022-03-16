"""j2cl_library build macro

Takes Java source, translates it into Closure style JS and surfaces it to the
rest of the build tree with a js_common.provider. Generally library rules dep on
other library rules for reference resolution and this build macro is no
exception. In particular the deps this rule needs for reference resolution are
java_library() targets which will have been created by other invocations of
this same j2cl_library build macro.


Example use:

# Effectively creates closure_js_library(name="Foo") containing translated JS.
j2cl_library(
    name = "Foo",
    srcs = glob(["Foo.java"]),
    deps = [":Bar"]  # Directly depends on j2cl_library(name="Bar")
)

# Effectively creates closure_js_library(name="Bar") containing the results.
j2cl_library(
    name = "Bar",
    srcs = glob(["Bar.java"]),
)

"""

load(":j2cl_library_build_test.bzl", "build_test")
load(":j2cl_common.bzl", "J2clInfo")
load(":j2cl_java_library.bzl", j2cl_library_rule = "j2cl_library")
load(":j2cl_util.bzl", "to_parallel_targets")
load(":j2kt_common.bzl", "j2kt_common")
load(":j2kt_library.bzl", "J2KT_LIB_ATTRS", "j2kt_jvm_library", "j2kt_native_library")
load(":j2wasm_common.bzl", "j2wasm_common")
load(":j2wasm_library.bzl", "J2WASM_LIB_ATTRS", "j2wasm_library")

# Packages that j2cl rule will generage j2kt jvm packages by default. Used to simplify test
# rules.
_J2KT_JVM_PACKAGES = [
    "transpiler/javatests/com/google/j2cl/readable/java",
]

# Packages that j2cl rule will generage j2kt native packages by default. Used to simplify test
# rules.
_J2KT_NATIVE_PACKAGES = []

_J2WASM_PACKAGES = [
    "third_party/java/auto",
    "third_party/java/jbox2d",
    "third_party/java_src/jbox2d/jbox2dlibrary/src/main/java",
    "third_party/java/jsr250_annotations",
    "third_party/java/jsr330_inject",
    "third_party/java/re2j",
    "third_party/java_src/google_common/current",
    "third_party/java_src/j2cl",
    "third_party/java_src/jsr330_inject",
    "third_party/java_src/re2j",
]

_KOTLIN_OPT_IN_PACKAGES = [
]

def _tree_artifact_proxy_impl(ctx):
    js_files = ctx.attr.j2cl_library[J2clInfo]._private_.output_js
    return DefaultInfo(files = depset([js_files]), runfiles = ctx.runfiles([js_files]))

_tree_artifact_proxy = rule(
    implementation = _tree_artifact_proxy_impl,
    attrs = {"j2cl_library": attr.label()},
)

def j2cl_library(
        name,
        generate_build_test = None,
        generate_j2kt_native_library = None,
        generate_j2kt_jvm_library = None,
        generate_j2wasm_library = None,
        **kwargs):
    """Translates Java source into JS source encapsulated by a JsInfo provider.

    See j2cl_java_library.bzl#j2cl_library for the arguments.

    Implicit output targets:
      lib<name>.jar: A java archive containing the byte code.
      lib<name>-src.jar: A java archive containing the sources (source jar).
    """
    args = dict(kwargs)

    # If this is JRE itself, don't synthesize the JRE dep.
    target_name = "//" + native.package_name() + ":" + name
    if args.get("srcs") and target_name != "//jre/java:jre":
        jre = Label("//:jre")
        args["deps"] = args.get("deps", []) + [jre]

    # TODO(b/217287994): Replace with more traditional allow-listing.
    kotlin_allowed = any([p for p in _KOTLIN_OPT_IN_PACKAGES if native.package_name().startswith(p)])
    if kotlin_allowed:
        args["j2cl_transpiler_override"] = (
            "//build_defs/internal_do_not_use:BazelJ2clBuilderWithKolinSupport"
        )

    j2cl_library_rule(
        name = name,
        **args
    )

    # TODO(b/36549068): remove this workaround when tree artifacts can be
    # declared as the rule output.
    _tree_artifact_proxy(
        name = name + ".js",
        j2cl_library = ":" + name,
        visibility = ["//visibility:private"],
        tags = ["manual", "notap", "no-ide"],
        testonly = args.get("testonly", 0),
    )

    if args.get("srcs") and (generate_build_test == None or generate_build_test):
        build_test(name, kwargs.get("tags", []))

    j2wasm_library_name = j2wasm_common.to_j2wasm_name(name)

    if generate_j2wasm_library == None:
        # By default refer back to allow list for implicit j2wasm target generation.
        generate_j2wasm_library = (
            not native.existing_rule(j2wasm_library_name) and
            any([p for p in _J2WASM_PACKAGES if native.package_name().startswith(p)])
        )

    if generate_j2wasm_library:
        j2wasm_args = _filter_j2wasm_attrs(dict(kwargs))

        to_parallel_targets("deps", j2wasm_args, j2wasm_common.to_j2wasm_name)
        to_parallel_targets("exports", j2wasm_args, j2wasm_common.to_j2wasm_name)
        j2wasm_args["tags"] = j2wasm_args.get("tags", []) + ["manual", "notap", "j2wasm", "no-ide"]

        j2wasm_library(
            name = j2wasm_library_name,
            **j2wasm_args
        )

    j2kt_native_library_name = j2kt_common.to_j2kt_native_name(name)

    if generate_j2kt_native_library == None:
        # By default refer back to allow list for implicit j2kt target generation.
        generate_j2kt_native_library = (
            not native.existing_rule(j2kt_native_library_name) and
            any([p for p in _J2KT_NATIVE_PACKAGES if native.package_name().startswith(p)])
        )

    if generate_j2kt_native_library:
        j2kt_args = _filter_j2kt_attrs(dict(kwargs))

        to_parallel_targets("deps", j2kt_args, j2kt_common.to_j2kt_native_name)
        to_parallel_targets("exports", j2kt_args, j2kt_common.to_j2kt_native_name)
        j2kt_args["tags"] = j2kt_args.get("tags", []) + ["j2kt", "ios"]

        j2kt_native_library(
            name = j2kt_native_library_name,
            **j2kt_args
        )

    j2kt_jvm_library_name = j2kt_common.to_j2kt_jvm_name(name)

    if generate_j2kt_jvm_library == None:
        # By default refer back to allow list for implicit j2kt target generation.
        generate_j2kt_jvm_library = (
            not native.existing_rule(j2kt_jvm_library_name) and
            any([p for p in _J2KT_JVM_PACKAGES if native.package_name().startswith(p)])
        )

    if generate_j2kt_jvm_library:
        j2kt_args = _filter_j2kt_attrs(dict(kwargs))
        j2kt_args["tags"] = j2kt_args.get("tags", []) + ["j2kt"]

        to_parallel_targets("deps", j2kt_args, j2kt_common.to_j2kt_jvm_name)
        to_parallel_targets("exports", j2kt_args, j2kt_common.to_j2kt_jvm_name)

        j2kt_jvm_library(
            name = j2kt_jvm_library_name,
            **j2kt_args
        )

_ALLOWED_ATTRS_KT = [key for key in J2KT_LIB_ATTRS] + ["tags", "visibility"]

def _filter_j2kt_attrs(args):
    return {key: args[key] for key in _ALLOWED_ATTRS_KT if key in args}

_ALLOWED_ATTRS_WASM = [key for key in J2WASM_LIB_ATTRS] + ["tags", "visibility"]

def _filter_j2wasm_attrs(args):
    return {key: args[key] for key in _ALLOWED_ATTRS_WASM if key in args}
