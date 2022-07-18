/*
 * Copyright 2022 Google Inc.
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
package j2ktjre;

import static com.google.j2cl.integration.testing.Asserts.assertEquals;

import java.math.MathContext;
import java.math.RoundingMode;

public class Math {

  static void testMath() {
    String s = "precision=5 roundingMode=HALF_UP";
    assertEquals(new MathContext(5, RoundingMode.HALF_UP), new MathContext(s));
    assertEquals(s, new MathContext(5, RoundingMode.HALF_UP).toString());
  }
}
