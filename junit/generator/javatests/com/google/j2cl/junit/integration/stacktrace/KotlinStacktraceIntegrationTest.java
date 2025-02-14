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
package com.google.j2cl.junit.integration.stacktrace;

import static org.junit.Assume.assumeFalse;

import com.google.j2cl.junit.integration.IntegrationTestBase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Integration test for stack trace deobfuscation */
@RunWith(Parameterized.class)
public class KotlinStacktraceIntegrationTest extends IntegrationTestBase {

  @Before
  public void assumeNonJ2wasm() {
    // No kotlin to wasm tests
    assumeFalse(testMode.isJ2wasm());
  }

  @Test
  public void testAnonymousClasses() throws Exception {
    runStacktraceTest("KotlinAnonymousClassesStacktraceTest");
  }

  @Test
  public void testSimpleThrowingMethod() throws Exception {
    runStacktraceTest("SimpleKotlinThrowingStacktraceTest");
  }

  @Test
  public void testCustomException() throws Exception {
    runStacktraceTest("KotlinCustomExceptionStacktraceTest");
  }

  @Test
  public void testFillInStackTrace() throws Exception {
    runStacktraceTest("KotlinFillInStacktraceTest");
  }

  @Test
  public void testJsException() throws Exception {
    runStacktraceTest("KotlinJsExceptionStacktraceTest");
  }

  @Test
  public void testLambda() throws Exception {
    runStacktraceTest("KotlinLambdaStacktraceTest");
  }

  @Test
  public void testRecursive() throws Exception {
    runStacktraceTest("KotlinRecursiveStacktraceTest");
  }
}
