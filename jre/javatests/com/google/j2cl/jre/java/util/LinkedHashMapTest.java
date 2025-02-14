/*
 * Copyright 2006 Google Inc.
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
package com.google.j2cl.jre.java.util;

import com.google.j2cl.jre.testing.J2ktIncompatible;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/** Tests <code>LinkedHashMap</code>. */
public class LinkedHashMapTest extends TestMap {
  // should be a method-level class, however to avoid serialization warning made
  // static instead.

  @J2ktIncompatible // LinkedHashMap is final in J2kt; LRU unsupported
  static class TestRemoveEldestMap<K, V> extends LinkedHashMap<K, V> {

    public K expectedKey;
    public boolean removeEldest;

    public TestRemoveEldestMap() {
      this(false);
    }

    public TestRemoveEldestMap(boolean accessOrder) {
      super(1, .5f, accessOrder);
    }

    @Override
    public boolean removeEldestEntry(Map.Entry<K, V> entry) {
      if (removeEldest) {
        assertEquals(expectedKey, entry.getKey());
        return true;
      } else {
        return false;
      }
    }
  }

  private static final int CAPACITY_16 = 16;
  private static final int CAPACITY_NEG_ONE_HALF = -1;
  private static final int CAPACITY_ZERO = 0;
  private static final Integer INTEGER_1 = new Integer(1);
  private static final Integer INTEGER_11 = new Integer(11);
  private static final Integer INTEGER_2 = new Integer(2);
  private static final Integer INTEGER_22 = new Integer(22);
  private static final Integer INTEGER_3 = new Integer(3);
  private static final Integer INTEGER_33 = new Integer(33);
  private static final Integer INTEGER_ZERO_KEY = new Integer(0);
  private static final String INTEGER_ZERO_VALUE = "integer zero";
  private static final String KEY_1 = "key1";
  private static final String KEY_2 = "key2";
  private static final String KEY_3 = "key3";
  private static final String KEY_4 = "key4";
  private static final String KEY_KEY = "key";
  private static final String KEY_TEST_CONTAINS_KEY = "testContainsKey";
  private static final String KEY_TEST_CONTAINS_VALUE = "testContainsValue";
  private static final String KEY_TEST_ENTRY_SET = "testEntrySet";
  private static final String KEY_TEST_GET = "testGet";
  private static final String KEY_TEST_KEY_SET = "testKeySet";
  private static final String KEY_TEST_PUT = "testPut";
  private static final String KEY_TEST_REMOVE = "testRemove";
  private static final float LOAD_FACTOR_NEG_ONE = -1.0F;
  private static final float LOAD_FACTOR_ONE_HALF = 0.5F;
  private static final float LOAD_FACTOR_ONE_TENTH = 0.1F;
  private static final float LOAD_FACTOR_ZERO = 0.0F;
  private static final Object ODD_ZERO_KEY =
      new Object() {
        @Override
        public int hashCode() {
          return 0;
        }
      };
  private static final String ODD_ZERO_VALUE = "odd zero";
  private static final int SIZE_ONE = 1;
  private static final int SIZE_THREE = 3;
  private static final int SIZE_TWO = 2;
  private static final int SIZE_ZERO = 0;
  private static final String STRING_ZERO_KEY = "0";
  private static final String STRING_ZERO_VALUE = "string zero";
  private static final String VALUE_1 = "val1";
  private static final String VALUE_2 = "val2";
  private static final String VALUE_3 = "val3";
  private static final String VALUE_4 = "val4";
  private static final String VALUE_TEST_CONTAINS_DOES_NOT_EXIST = "does not exist";
  private static final Integer VALUE_TEST_CONTAINS_KEY = new Integer(5);
  private static final String VALUE_TEST_ENTRY_SET_1 = KEY_TEST_ENTRY_SET + " - value1";
  private static final String VALUE_TEST_ENTRY_SET_2 = KEY_TEST_ENTRY_SET + " - value2";
  private static final String VALUE_TEST_GET = KEY_TEST_GET + " - Value";
  private static final String VALUE_TEST_KEY_SET = KEY_TEST_KEY_SET + " - value";
  private static final String VALUE_TEST_PUT_1 = KEY_TEST_PUT + " - value 1";
  private static final String VALUE_TEST_PUT_2 = KEY_TEST_PUT + " - value 2";
  private static final String VALUE_TEST_REMOVE = KEY_TEST_REMOVE + " - value";
  private static final String VALUE_VAL = "value";

  /**
   * Check the state of a newly constructed, empty LinkedHashMap.
   *
   * @param hashMap
   */
  private static void checkEmptyLinkedHashMapAssumptions(LinkedHashMap<?, ?> hashMap) {
    assertNotNull(hashMap);
    assertTrue(hashMap.isEmpty());

    assertNotNull(hashMap.values());
    assertTrue(hashMap.values().isEmpty());
    assertTrue(hashMap.values().size() == 0);

    assertNotNull(hashMap.keySet());
    assertTrue(hashMap.keySet().isEmpty());
    assertTrue(hashMap.keySet().size() == 0);

    assertNotNull(hashMap.entrySet());
    assertTrue(hashMap.entrySet().isEmpty());
    assertTrue(hashMap.entrySet().size() == 0);

    assertNotNull(hashMap.entrySet().iterator());
    assertFalse(hashMap.entrySet().iterator().hasNext());
  }

  public void testAddEqualKeys() {
    final LinkedHashMap<Number, Object> expected = new LinkedHashMap<Number, Object>();
    assertEquals(0, expected.size());
    iterateThrough(expected);
    expected.put(new Long(45), new Object());
    assertEquals(1, expected.size());
    iterateThrough(expected);
    expected.put(new Integer(45), new Object());
    assertNotSame(new Integer(45), new Long(45));
    assertEquals(2, expected.size());
    iterateThrough(expected);
  }

  public void testAddWatch() {
    LinkedHashMap<String, String> m = new LinkedHashMap<String, String>();
    m.put("watch", "watch");
    assertEquals("watch", m.get("watch"));
  }

  /*
   * Test method for 'java.util.LinkedHashMap.clear()'
   */
  public void testClear() {
    LinkedHashMap<String, String> hashMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(hashMap);

    hashMap.put("Hello", "Bye");
    assertFalse(hashMap.isEmpty());
    assertTrue(hashMap.size() == SIZE_ONE);

    hashMap.clear();
    assertTrue(hashMap.isEmpty());
    assertTrue(hashMap.size() == 0);
  }

  /*
   * Test method for 'java.util.LinkedHashMap.clone()'
   */
  @SuppressWarnings("unchecked")
  @J2ktIncompatible // b/317230935
  public void testClone() {
    LinkedHashMap<String, String> srcMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(srcMap);

    // Check empty clone behavior
    LinkedHashMap<String, String> dstMap = (LinkedHashMap<String, String>) srcMap.clone();
    assertNotNull(dstMap);
    assertEquals(dstMap.size(), srcMap.size());
    assertEquals(dstMap.keySet().toArray(), srcMap.keySet().toArray());
    assertEquals(dstMap.entrySet().toArray(), srcMap.entrySet().toArray());

    // Check non-empty clone behavior
    srcMap.put(KEY_1, VALUE_1);
    srcMap.put(KEY_2, VALUE_2);
    srcMap.put(KEY_3, VALUE_3);
    srcMap.put(KEY_4, VALUE_4);
    dstMap = (LinkedHashMap<String, String>) srcMap.clone();
    assertNotNull(dstMap);
    assertEquals(dstMap.size(), srcMap.size());
    assertEquals(dstMap.keySet().toArray(), srcMap.keySet().toArray());
    assertEquals(dstMap.entrySet().toArray(), srcMap.entrySet().toArray());
  }

  /*
   * Test method for 'java.util.LinkedHashMap.containsKey(Object)'
   */
  public void testContainsKey() {
    LinkedHashMap<@Nullable String, @Nullable Integer> hashMap = new LinkedHashMap<>();
    checkEmptyLinkedHashMapAssumptions(hashMap);

    assertFalse(hashMap.containsKey(KEY_TEST_CONTAINS_KEY));
    hashMap.put(KEY_TEST_CONTAINS_KEY, VALUE_TEST_CONTAINS_KEY);
    assertTrue(hashMap.containsKey(KEY_TEST_CONTAINS_KEY));
    assertFalse(hashMap.containsKey(VALUE_TEST_CONTAINS_DOES_NOT_EXIST));

    assertFalse(hashMap.containsKey(null));
    hashMap.put(null, VALUE_TEST_CONTAINS_KEY);
    assertTrue(hashMap.containsKey(null));
  }

  /*
   * Test method for 'java.util.LinkedHashMap.containsValue(Object)'
   */
  @SuppressWarnings("CollectionIncompatibleType")
  public void testContainsValue() {
    LinkedHashMap<@Nullable String, @Nullable Integer> hashMap = new LinkedHashMap<>();
    checkEmptyLinkedHashMapAssumptions(hashMap);

    assertFalse("check contains of empty map", hashMap.containsValue(VALUE_TEST_CONTAINS_KEY));
    hashMap.put(KEY_TEST_CONTAINS_VALUE, VALUE_TEST_CONTAINS_KEY);
    assertTrue(
        "check contains of map with element", hashMap.containsValue(VALUE_TEST_CONTAINS_KEY));
    assertFalse(
        "check contains of map other element",
        hashMap.containsValue(
            /* expected: Integer, actual: String */ VALUE_TEST_CONTAINS_DOES_NOT_EXIST));

    assertFalse(hashMap.containsValue(null));
    hashMap.put(KEY_TEST_CONTAINS_VALUE, null);
    assertTrue(hashMap.containsValue(null));
  }

  /*
   * Test method for 'java.util.LinkedHashMap.entrySet()'
   */
  public void testEntrySet() {
    LinkedHashMap<String, String> hashMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(hashMap);

    Set<Entry<String, String>> entrySet = hashMap.entrySet();
    assertNotNull(entrySet);

    // Check that the entry set looks right
    hashMap.put(KEY_TEST_ENTRY_SET, VALUE_TEST_ENTRY_SET_1);
    entrySet = hashMap.entrySet();
    assertEquals(SIZE_ONE, entrySet.size());
    Iterator<Entry<String, String>> itSet = entrySet.iterator();
    Map.Entry<String, String> entry = itSet.next();
    assertEquals(KEY_TEST_ENTRY_SET, entry.getKey());
    assertEquals(VALUE_TEST_ENTRY_SET_1, entry.getValue());

    // Check that entries in the entrySet are update correctly on overwrites
    hashMap.put(KEY_TEST_ENTRY_SET, VALUE_TEST_ENTRY_SET_2);
    entrySet = hashMap.entrySet();
    assertEquals(SIZE_ONE, entrySet.size());
    itSet = entrySet.iterator();
    entry = itSet.next();
    assertEquals(KEY_TEST_ENTRY_SET, entry.getKey());
    assertEquals(VALUE_TEST_ENTRY_SET_2, entry.getValue());

    // Check that entries are updated on removes
    hashMap.remove(KEY_TEST_ENTRY_SET);
    checkEmptyLinkedHashMapAssumptions(hashMap);
  }

  /*
   * Used to test the entrySet remove method.
   */
  public void testEntrySetRemove() {
    LinkedHashMap<String, String> hashMap = new LinkedHashMap<String, String>();
    hashMap.put("A", "B");
    LinkedHashMap<String, String> dummy = new LinkedHashMap<String, String>();
    dummy.put("A", "b");
    Entry<String, String> bogus = dummy.entrySet().iterator().next();
    Set<Entry<String, String>> entrySet = hashMap.entrySet();
    boolean removed = entrySet.remove(bogus);
    assertEquals(false, removed);
    assertEquals("B", hashMap.get("A"));
  }

  /*
   * Test method for 'java.util.AbstractMap.equals(Object)'
   */
  // public void testEquals() {
  // LinkedHashMap hashMap = new LinkedHashMap();
  // checkEmptyLinkedHashMapAssumptions(hashMap);
  //
  // hashMap.put(KEY_KEY, VALUE_VAL);
  //
  // LinkedHashMap copyMap = (LinkedHashMap) hashMap.clone();
  //
  // assertTrue(hashMap.equals(copyMap));
  // hashMap.put(VALUE_VAL, KEY_KEY);
  // assertFalse(hashMap.equals(copyMap));
  // }
  /*
   * Test method for 'java.lang.Object.finalize()'.
   */
  public void testFinalize() {
    // no tests for finalize
  }

  /*
   * Test method for 'java.util.LinkedHashMap.get(Object)'.
   */
  public void testGet() {
    LinkedHashMap<@Nullable String, @Nullable String> hashMap = new LinkedHashMap<>();
    checkEmptyLinkedHashMapAssumptions(hashMap);

    assertNull(hashMap.get(KEY_TEST_GET));
    hashMap.put(KEY_TEST_GET, VALUE_TEST_GET);
    assertNotNull(hashMap.get(KEY_TEST_GET));

    assertNull(hashMap.get(null));
    hashMap.put(null, VALUE_TEST_GET);
    assertNotNull(hashMap.get(null));

    hashMap.put(null, null);
    assertNull(hashMap.get(null));
  }

  /*
   * Test method for 'java.util.AbstractMap.hashCode()'.
   */
  public void testHashCode() {
    LinkedHashMap<String, String> hashMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(hashMap);

    // Check that hashCode changes
    int hashCode1 = hashMap.hashCode();
    hashMap.put(KEY_KEY, VALUE_VAL);
    int hashCode2 = hashMap.hashCode();

    assertTrue(hashCode1 != hashCode2);
  }

  /*
   * Test method for 'java.util.AbstractMap.isEmpty()'
   */
  public void testIsEmpty() {
    LinkedHashMap<String, String> srcMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(srcMap);

    LinkedHashMap<String, String> dstMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(dstMap);

    dstMap.putAll(srcMap);
    assertTrue(dstMap.isEmpty());

    dstMap.put(KEY_KEY, VALUE_VAL);
    assertFalse(dstMap.isEmpty());

    dstMap.remove(KEY_KEY);
    assertTrue(dstMap.isEmpty());
    assertEquals(0, dstMap.size());
  }

  public void testKeysConflict() {
    LinkedHashMap<Object, String> hashMap = new LinkedHashMap<Object, String>();

    hashMap.put(STRING_ZERO_KEY, STRING_ZERO_VALUE);
    hashMap.put(INTEGER_ZERO_KEY, INTEGER_ZERO_VALUE);
    hashMap.put(ODD_ZERO_KEY, ODD_ZERO_VALUE);
    assertEquals(INTEGER_ZERO_VALUE, hashMap.get(INTEGER_ZERO_KEY));
    assertEquals(ODD_ZERO_VALUE, hashMap.get(ODD_ZERO_KEY));
    assertEquals(STRING_ZERO_VALUE, hashMap.get(STRING_ZERO_KEY));
    hashMap.remove(INTEGER_ZERO_KEY);
    assertEquals(ODD_ZERO_VALUE, hashMap.get(ODD_ZERO_KEY));
    assertEquals(STRING_ZERO_VALUE, hashMap.get(STRING_ZERO_KEY));
    assertEquals(hashMap.get(INTEGER_ZERO_KEY), null);
    hashMap.remove(ODD_ZERO_KEY);
    assertEquals(hashMap.get(INTEGER_ZERO_KEY), null);
    assertEquals(hashMap.get(ODD_ZERO_KEY), null);
    assertEquals(STRING_ZERO_VALUE, hashMap.get(STRING_ZERO_KEY));
    hashMap.remove(STRING_ZERO_KEY);
    assertEquals(hashMap.get(INTEGER_ZERO_KEY), null);
    assertEquals(hashMap.get(ODD_ZERO_KEY), null);
    assertEquals(hashMap.get(STRING_ZERO_KEY), null);
    assertEquals(0, hashMap.size());
  }

  /*
   * Test method for 'java.util.LinkedHashMap.keySet()'
   */
  public void testKeySet() {
    LinkedHashMap<String, String> hashMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(hashMap);

    Set<String> keySet = hashMap.keySet();
    assertNotNull(keySet);
    assertTrue(keySet.isEmpty());
    assertTrue(keySet.size() == 0);

    hashMap.put(KEY_TEST_KEY_SET, VALUE_TEST_KEY_SET);
    assertEquals(SIZE_ONE, keySet.size());
    assertTrue(keySet.contains(KEY_TEST_KEY_SET));
    assertFalse(keySet.contains(VALUE_TEST_KEY_SET));
    assertFalse(keySet.contains(KEY_TEST_KEY_SET.toUpperCase(Locale.ROOT)));
  }

  /*
   * Test method for 'java.util.LinkedHashMap.LinkedHashMap()'.
   */
  public void testLinkedHashMap() {
    LinkedHashMap<String, String> hashMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(hashMap);
  }

  /*
   * Test method for 'java.util.LinkedHashMap.LinkedHashMap(int)'
   */
  public void testLinkedHashMapInt() {
    LinkedHashMap<String, String> hashMap = new LinkedHashMap<String, String>(CAPACITY_16);
    checkEmptyLinkedHashMapAssumptions(hashMap);

    // TODO(mmendez): how do we verify capacity?
    boolean failed = true;
    try {
      new LinkedHashMap<String, String>(-SIZE_ONE);
    } catch (Throwable ex) {
      if (ex instanceof IllegalArgumentException) {
        failed = false;
      }
    }

    if (failed) {
      fail("Failure testing new LinkedHashMap(-1)");
    }

    LinkedHashMap<String, String> zeroSizedLinkedHashMap = new LinkedHashMap<String, String>(0);
    assertNotNull(zeroSizedLinkedHashMap);
  }

  /*
   * Test method for 'java.util.LinkedHashMap.LinkedHashMap(int, float)'
   */
  public void testLinkedHashMapIntFloat() {

    LinkedHashMap<String, String> hashMap =
        new LinkedHashMap<String, String>(CAPACITY_16, LOAD_FACTOR_ONE_HALF);
    checkEmptyLinkedHashMapAssumptions(hashMap);

    // TODO(mmendez): how do we verify capacity and load factor?

    // Test new LinkedHashMap(-1, 0.0F)
    boolean failed = true;
    try {
      new LinkedHashMap<String, String>(CAPACITY_NEG_ONE_HALF, LOAD_FACTOR_ZERO);
    } catch (Throwable ex) {
      if (ex instanceof IllegalArgumentException) {
        failed = false;
      }
    }

    if (failed) {
      fail("Failure testing new LinkedHashMap(-1, 0.0F)");
    }

    // Test new LinkedHashMap(0, -1.0F)
    failed = true;
    try {
      new LinkedHashMap<String, String>(CAPACITY_ZERO, LOAD_FACTOR_NEG_ONE);
    } catch (Throwable ex) {
      if (ex instanceof IllegalArgumentException) {
        failed = false;
      }
    }

    if (failed) {
      fail("Failure testing new LinkedHashMap(0, -1.0F)");
    }

    // Test new LinkedHashMap(0,0F);
    hashMap = new LinkedHashMap<String, String>(CAPACITY_ZERO, LOAD_FACTOR_ONE_TENTH);
    assertNotNull(hashMap);
  }

  /*
   * Test method for 'java.util.LinkedHashMap.LinkedHashMap(Map)'
   */
  public void testLinkedHashMapMap() {
    LinkedHashMap<Integer, Integer> srcMap = new LinkedHashMap<Integer, Integer>();
    assertNotNull(srcMap);
    checkEmptyLinkedHashMapAssumptions(srcMap);

    srcMap.put(INTEGER_1, INTEGER_11);
    srcMap.put(INTEGER_2, INTEGER_22);
    srcMap.put(INTEGER_3, INTEGER_33);

    LinkedHashMap<Integer, Integer> hashMap = new LinkedHashMap<Integer, Integer>(srcMap);
    assertFalse(hashMap.isEmpty());
    assertTrue(hashMap.size() == SIZE_THREE);

    Collection<Integer> valColl = hashMap.values();
    assertTrue(valColl.contains(INTEGER_11));
    assertTrue(valColl.contains(INTEGER_22));
    assertTrue(valColl.contains(INTEGER_33));

    Collection<Integer> keyColl = hashMap.keySet();
    assertTrue(keyColl.contains(INTEGER_1));
    assertTrue(keyColl.contains(INTEGER_2));
    assertTrue(keyColl.contains(INTEGER_3));
  }

  @J2ktIncompatible // LRU unsupported
  public void testLRU() {
    LinkedHashMap<String, String> m = new LinkedHashMap<String, String>(10, .5f, true);
    m.put("A", "A");
    m.put("B", "B");
    m.put("C", "C");
    m.put("D", "D");
    Iterator<Entry<String, String>> entry = m.entrySet().iterator();
    assertEquals("A", entry.next().getValue());
    assertEquals("B", entry.next().getValue());
    assertEquals("C", entry.next().getValue());
    assertEquals("D", entry.next().getValue());
    m.get("B");
    m.get("D");
    entry = m.entrySet().iterator();
    assertEquals("A", entry.next().getValue());
    assertEquals("C", entry.next().getValue());
    assertEquals("B", entry.next().getValue());
    assertEquals("D", entry.next().getValue());
  }

  /*
   * Test method for 'java.util.LinkedHashMap.put(Object, Object)'
   */
  public void testPut() {
    LinkedHashMap<@Nullable String, @Nullable String> hashMap = new LinkedHashMap<>();
    checkEmptyLinkedHashMapAssumptions(hashMap);

    assertNull(hashMap.put(KEY_TEST_PUT, VALUE_TEST_PUT_1));
    assertEquals(VALUE_TEST_PUT_1, hashMap.put(KEY_TEST_PUT, VALUE_TEST_PUT_2));
    assertNull(hashMap.put(null, VALUE_TEST_PUT_1));
    assertEquals(VALUE_TEST_PUT_1, hashMap.put(null, VALUE_TEST_PUT_2));
  }

  /** Test method for 'java.util.LinkedHashMap.putAll(Map)'. */
  public void testPutAll() {
    LinkedHashMap<String, String> srcMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(srcMap);

    srcMap.put(KEY_1, VALUE_1);
    srcMap.put(KEY_2, VALUE_2);
    srcMap.put(KEY_3, VALUE_3);

    // Make sure that the data is copied correctly
    LinkedHashMap<String, String> dstMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(dstMap);

    dstMap.putAll(srcMap);
    assertEquals(srcMap.size(), dstMap.size());
    assertTrue(dstMap.containsKey(KEY_1));
    assertTrue(dstMap.containsValue(VALUE_1));
    assertFalse(dstMap.containsKey(KEY_1.toUpperCase(Locale.ROOT)));
    assertFalse(dstMap.containsValue(VALUE_1.toUpperCase(Locale.ROOT)));

    assertTrue(dstMap.containsKey(KEY_2));
    assertTrue(dstMap.containsValue(VALUE_2));
    assertFalse(dstMap.containsKey(KEY_2.toUpperCase(Locale.ROOT)));
    assertFalse(dstMap.containsValue(VALUE_2.toUpperCase(Locale.ROOT)));

    assertTrue(dstMap.containsKey(KEY_3));
    assertTrue(dstMap.containsValue(VALUE_3));
    assertFalse(dstMap.containsKey(KEY_3.toUpperCase(Locale.ROOT)));
    assertFalse(dstMap.containsValue(VALUE_3.toUpperCase(Locale.ROOT)));

    // Check that an empty map does not blow away the contents of the
    // destination map
    LinkedHashMap<String, String> emptyMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(emptyMap);
    dstMap.putAll(emptyMap);
    assertTrue(dstMap.size() == srcMap.size());

    // Check that put all overwrite any existing mapping in the destination map
    srcMap.put(KEY_1, VALUE_2);
    srcMap.put(KEY_2, VALUE_3);
    srcMap.put(KEY_3, VALUE_1);

    dstMap.putAll(srcMap);
    assertEquals(dstMap.size(), srcMap.size());
    assertEquals(VALUE_2, dstMap.get(KEY_1));
    assertEquals(VALUE_3, dstMap.get(KEY_2));
    assertEquals(VALUE_1, dstMap.get(KEY_3));

    // Check that a putAll does adds data but does not remove it

    srcMap.put(KEY_4, VALUE_4);
    dstMap.putAll(srcMap);
    assertEquals(dstMap.size(), srcMap.size());
    assertTrue(dstMap.containsKey(KEY_4));
    assertTrue(dstMap.containsValue(VALUE_4));
    assertEquals(VALUE_2, dstMap.get(KEY_1));
    assertEquals(VALUE_3, dstMap.get(KEY_2));
    assertEquals(VALUE_1, dstMap.get(KEY_3));
    assertEquals(VALUE_4, dstMap.get(KEY_4));

    dstMap.putAll(dstMap);
  }

  /** Test method for 'java.util.LinkedHashMap.remove(Object)'. */
  public void testRemove() {
    LinkedHashMap<@Nullable String, @Nullable String> hashMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(hashMap);

    assertNull(hashMap.remove(null));
    hashMap.put(null, VALUE_TEST_REMOVE);
    assertNotNull(hashMap.remove(null));

    hashMap.put(KEY_TEST_REMOVE, VALUE_TEST_REMOVE);
    assertEquals(VALUE_TEST_REMOVE, hashMap.remove(KEY_TEST_REMOVE));
    assertNull(hashMap.remove(KEY_TEST_REMOVE));
  }

  @J2ktIncompatible // see TestRemoveEldestMap
  public void testRemoveEldest() {
    TestRemoveEldestMap<String, String> m = new TestRemoveEldestMap<String, String>(false);
    m.put("A", "A");
    m.put("B", "B");
    m.put("C", "C");
    m.put("D", "D");
    m.get("B");
    m.get("D");
    m.removeEldest = true;
    m.expectedKey = "A";
    m.put("E", "E");
    m.put("B", "New-B");
    Iterator<Map.Entry<String, String>> entries = m.entrySet().iterator();
    Map.Entry<String, String> first = entries.next();
    assertEquals("B", first.getKey());
    assertEquals("New-B", first.getValue());
    assertEquals(4, m.size());
  }

  @J2ktIncompatible // see TestRemoveEldestMap
  public void testRemoveEldestMapLRU() {
    TestRemoveEldestMap<String, String> m = new TestRemoveEldestMap<String, String>(true);
    m.put("A", "A");
    m.put("B", "B");
    m.put("C", "C");
    m.put("D", "D");
    m.get("A");
    m.get("D");
    m.removeEldest = true;
    m.expectedKey = "B";
    m.put("E", "E");

    m.put("C", "New-C");
    Iterator<Map.Entry<String, String>> entries = m.entrySet().iterator();
    Map.Entry<String, String> first = entries.next();
    assertEquals("A", first.getKey());
    assertEquals("D", entries.next().getKey());
    assertEquals("E", entries.next().getKey());
    assertEquals("New-C", entries.next().getValue());
  }

  /** Test method for 'java.util.LinkedHashMap.size()'. */
  public void testSize() {
    LinkedHashMap<String, String> hashMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(hashMap);

    // Test size behavior on put
    assertEquals(SIZE_ZERO, hashMap.size());
    hashMap.put(KEY_1, VALUE_1);
    assertEquals(SIZE_ONE, hashMap.size());
    hashMap.put(KEY_2, VALUE_2);
    assertEquals(SIZE_TWO, hashMap.size());
    hashMap.put(KEY_3, VALUE_3);
    assertEquals(SIZE_THREE, hashMap.size());

    // Test size behavior on remove
    hashMap.remove(KEY_1);
    assertEquals(SIZE_TWO, hashMap.size());
    hashMap.remove(KEY_2);
    assertEquals(SIZE_ONE, hashMap.size());
    hashMap.remove(KEY_3);
    assertEquals(SIZE_ZERO, hashMap.size());

    // Test size behavior on putAll
    hashMap.put(KEY_1, VALUE_1);
    hashMap.put(KEY_2, VALUE_2);
    hashMap.put(KEY_3, VALUE_3);
    LinkedHashMap<String, String> srcMap = new LinkedHashMap<String, String>(hashMap);
    hashMap.putAll(srcMap);
    assertEquals(SIZE_THREE, hashMap.size());

    // Test size behavior on clear
    hashMap.clear();
    assertEquals(SIZE_ZERO, hashMap.size());
  }

  /** Test method for 'java.util.AbstractMap.toString()'. */
  public void testToString() {
    LinkedHashMap<String, String> hashMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(hashMap);
    hashMap.put(KEY_KEY, VALUE_VAL);
    String entryString = makeEntryString(KEY_KEY, VALUE_VAL);
    assertTrue(entryString.equals(hashMap.toString()));
  }

  /** Test method for 'java.util.AbstractMap.values()'. */
  public void testValues() {
    LinkedHashMap<String, String> hashMap = new LinkedHashMap<String, String>();
    checkEmptyLinkedHashMapAssumptions(hashMap);

    assertNotNull(hashMap.values());

    hashMap.put(KEY_KEY, VALUE_VAL);

    Collection<String> valColl = hashMap.values();
    assertNotNull(valColl);
    assertEquals(SIZE_ONE, valColl.size());

    Iterator<String> itVal = valColl.iterator();
    String val = itVal.next();
    assertEquals(VALUE_VAL, val);
  }

  @Override
  protected Map<@Nullable Object, @Nullable Object> makeEmptyMap() {
    return new LinkedHashMap<@Nullable Object, @Nullable Object>();
  }

  private Iterator<Map.Entry<Number, Object>> iterateThrough(
      final LinkedHashMap<Number, Object> expected) {
    Iterator<Map.Entry<Number, Object>> iter = expected.entrySet().iterator();
    for (int i = 0; i < expected.size(); i++) {
      iter.next();
    }
    return iter;
  }

  private String makeEntryString(final String key, final String value) {
    return "{" + key + "=" + value + "}";
  }
}
