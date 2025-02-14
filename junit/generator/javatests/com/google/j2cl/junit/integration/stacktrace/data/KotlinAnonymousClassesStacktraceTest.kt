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

import kotlin.test.Test

/** Simple throwing test case */
class KotlinAnonymousClassesStacktraceTest : StacktraceTestBase() {

  @Test
  fun test() {
    val first = object : Runnable {
      override fun run() {
        if (true) {
          throw RuntimeException("__the_message__!")
        }
      }
    }

    val r = object: Runnable {
      override fun run() {
        first.run()
      }
    }

    r.run()
  }
}
