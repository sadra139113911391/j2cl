/*
 * Copyright 2016 Google Inc.
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
package com.google.j2cl.ast;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.j2cl.ast.annotations.Visitable;
import com.google.j2cl.common.J2clUtils;
import javax.annotation.Nullable;

/** Abstract base class for member descriptors. */
@Visitable
public abstract class MemberDescriptor extends Node
    implements HasJsNameInfo, HasReadableDescription, HasUnusableByJsSuppression {

  public abstract JsInfo getJsInfo();

  public abstract TypeDescriptor getEnclosingTypeDescriptor();

  /** Returns true if {@code typeDescriptor} is the enclosing class of this member. */
  public boolean isMemberOf(TypeDescriptor typeDescriptor) {
    return getEnclosingTypeDescriptor()
        .getQualifiedSourceName()
        .equals(typeDescriptor.getQualifiedSourceName());
  }

  /** Returns true if {@code thatMemberDescriptor} is in the same type as this member. */
  public boolean inSameTypeAs(MemberDescriptor thatMemberDescriptor) {
    return thatMemberDescriptor.isMemberOf(getEnclosingTypeDescriptor());
  }

  @Nullable
  public abstract String getName();

  public abstract Visibility getVisibility();

  @Override
  public abstract boolean isNative();

  public abstract boolean isStatic();

  public abstract boolean isFinal();

  public abstract boolean isPolymorphic();

  public abstract boolean isSynthetic();

  public boolean isMethod() {
    return false;
  }

  public boolean isField() {
    return false;
  }

  public boolean isConstructor() {
    return false;
  }

  public boolean isDefaultMethod() {
    return false;
  }

  public boolean isJsConstructor() {
    return getJsInfo().getJsMemberType() == JsMemberType.CONSTRUCTOR;
  }

  public boolean isJsPropertyGetter() {
    return getJsInfo().getJsMemberType() == JsMemberType.GETTER;
  }

  public boolean isJsPropertySetter() {
    return getJsInfo().getJsMemberType() == JsMemberType.SETTER;
  }

  public boolean isJsMethod() {
    return getJsInfo().getJsMemberType() == JsMemberType.METHOD;
  }

  public abstract boolean isJsFunction();

  public boolean isJsMember() {
    return getJsInfo().getJsMemberType() != JsMemberType.NONE;
  }

  public boolean isJsOverlay() {
    return getJsInfo().isJsOverlay();
  }

  public boolean isExtern() {
    return isNative() && hasExternNamespace();
  }

  private boolean hasExternNamespace() {
    checkArgument(isNative());
    // A native type descriptor is an extern if its namespace is the global namespace or if
    // it inherited the namespace from its (enclosing) extern type.
    return JsUtils.isGlobal(getJsNamespace())
        || (getEnclosingTypeDescriptor().isExtern()
            && getJsNamespace().equals(getEnclosingTypeDescriptor().getQualifiedJsName()));
  }


  @Override
  public String getSimpleJsName() {
    String jsName = getJsInfo().getJsName();
    return jsName != null ? jsName : getJsInfo().getJsMemberType().computeJsName(this);
  }

  @Override
  public String getJsNamespace() {
    String jsNamespace = getJsInfo().getJsNamespace();
    return jsNamespace == null ? getEnclosingTypeDescriptor().getQualifiedJsName() : jsNamespace;
  }

  public boolean hasJsNamespace() {
    return getJsInfo().getJsNamespace() != null;
  }

  /** Returns a qualified binary name for the member. */
  public String getQualifiedBinaryName() {
    return J2clUtils.format(
        "%s.%s", getEnclosingTypeDescriptor().getQualifiedBinaryName(), getName());
  }

  @Override
  public Node accept(Processor processor) {
    return Visitor_MemberDescriptor.visit(processor, this);
  }
}
