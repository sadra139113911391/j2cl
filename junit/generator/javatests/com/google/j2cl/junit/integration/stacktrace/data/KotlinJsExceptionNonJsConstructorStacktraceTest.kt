/*
 * Copyright 2024 Google Inc.
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
package com.google.j2cl.junit.integration.stacktrace.data

import jsinterop.annotations.JsType
import kotlin.test.Test

class KotlinJsExceptionNonJsConstructorStacktraceTest : StacktraceTestBase() {
  @Test
  fun test() {
    method2()
  }

  fun method2() {
    throw MyJsException("__the_message__!")
  }

  @JsType class MyJsException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)
}
