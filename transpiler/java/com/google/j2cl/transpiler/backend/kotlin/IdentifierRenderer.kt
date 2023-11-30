/*
 * Copyright 2021 Google Inc.
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
package com.google.j2cl.transpiler.backend.kotlin

import com.google.j2cl.transpiler.ast.HasName
import com.google.j2cl.transpiler.backend.kotlin.ast.Keywords
import com.google.j2cl.transpiler.backend.kotlin.common.inBackTicks
import com.google.j2cl.transpiler.backend.kotlin.common.letIf
import com.google.j2cl.transpiler.backend.kotlin.common.orIfNull
import com.google.j2cl.transpiler.backend.kotlin.source.Source
import com.google.j2cl.transpiler.backend.kotlin.source.Source.Companion.dotSeparated
import com.google.j2cl.transpiler.backend.kotlin.source.Source.Companion.source

internal fun Renderer.nameSource(hasName: HasName) =
  identifierSource(environment.identifier(hasName))

internal fun identifierSource(identifier: String): Source = source(identifier.identifierString)

internal val String.identifierString
  get() =
    replace("$", "___").let { withoutDollars ->
      withoutDollars.letIf(Keywords.isHard(withoutDollars) || !withoutDollars.isValidIdentifier) {
        withoutDollars.inBackTicks
      }
    }

internal fun Renderer.topLevelQualifiedNameSource(
  qualifiedName: String,
  optInQualifiedName: String? = null
): Source =
  qualifiedName.qualifiedNameToSimpleName().let { simpleName ->
    if (localNames.contains(simpleName) || environment.containsIdentifier(simpleName)) {
      qualifiedIdentifierSource(qualifiedName)
    } else {
      environment
        .qualifiedToNonAliasedSimpleName(qualifiedName)
        ?.let { identifierSource(it) }
        .orIfNull { qualifiedIdentifierSource(qualifiedName) }
        .also { optInQualifiedName?.let { environment.addOptInQualifiedName(it) } }
    }
  }

internal fun Renderer.extensionMemberQualifiedNameSource(qualifiedName: String): Source =
  identifierSource(environment.qualifiedToSimpleName(qualifiedName))

internal fun qualifiedIdentifierSource(identifier: String): Source =
  dotSeparated(identifier.qualifiedNameComponents().map(::identifierSource))

private val String.isValidIdentifier: Boolean
  get() = first().isValidIdentifierFirstChar && all { it.isValidIdentifierChar }

private val Char.isValidIdentifierChar: Boolean
  get() = isLetterOrDigit() || this == '_'

private val Char.isValidIdentifierFirstChar: Boolean
  get() = isValidIdentifierChar && !isDigit()
