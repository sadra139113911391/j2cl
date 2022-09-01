/*
 * Copyright 2022 Google Inc.
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
package javaemul.internal.ktstubs;

import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

// TODO(b/240329860): Remove when provided by the kotlin stdlib.
@JsType(namespace = "kotlin", name = "Unit")
public final class UnitKt {
  private UnitKt() {}

  private static final UnitKt INSTANCE = new UnitKt();

  @JsProperty(name = "f_INSTANCE__kotlin_Unit")
  public static final UnitKt getInstance() {
    return INSTANCE;
  }
}
