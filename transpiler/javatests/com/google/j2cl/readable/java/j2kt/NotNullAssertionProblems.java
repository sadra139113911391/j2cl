/*
 * Copyright 2024 Google Inc.
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
package j2kt;

import org.jspecify.nullness.NullMarked;
import org.jspecify.nullness.Nullable;

/** A test covering cases with unnecessary not-null {@code !!} assertions. */
@NullMarked
public class NotNullAssertionProblems {
  public void test() {
    accept1(null);

    accept2("foo", null);
    accept2("foo", nullableString());

    acceptVararg("foo", null);
    acceptVararg("foo", nullableString());
  }

  public static <T extends @Nullable Object> void accept1(T t) {}

  public static <T extends @Nullable Object> void accept2(T t1, T t2) {}

  public static <T extends @Nullable Object> void acceptVararg(T... varargs) {}

  public static @Nullable String nullableString() {
    return null;
  }
}
