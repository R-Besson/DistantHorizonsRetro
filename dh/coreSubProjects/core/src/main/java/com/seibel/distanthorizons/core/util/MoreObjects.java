/*
 * Copyright (C) 2014 The Guava Authors
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.seibel.distanthorizons.core.util;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import com.google.common.annotations.GwtCompatible;
import com.google.common.base.Optional;

/**
 * Helper functions that operate on any {@code Object}, and are not already provided in {@link
 * java.util.Objects}.
 *
 * <p>
 * See the Guava User Guide on <a
 * href="https://github.com/google/guava/wiki/CommonObjectUtilitiesExplained">writing {@code Object}
 * methods with {@code MoreObjects}</a>.
 *
 * @author Laurence Gonsalves
 * @since 18.0 (since 2.0 as {@code Objects})
 */
@GwtCompatible
public final class MoreObjects {

	/**
	 * Creates an instance of {@link ToStringHelper}.
	 *
	 * <p>
	 * This is helpful for implementing {@link Object#toString()}. Specification by example:
	 *
	 * {@snippet :
	 * // Returns "ClassName{}"
	 * MoreObjects.toStringHelper(this)
	 * .toString();
	 *
	 * // Returns "ClassName{x=1}"
	 * MoreObjects.toStringHelper(this)
	 * .add("x", 1)
	 * .toString();
	 *
	 * // Returns "MyObject{x=1}"
	 * MoreObjects.toStringHelper("MyObject")
	 * .add("x", 1)
	 * .toString();
	 *
	 * // Returns "ClassName{x=1, y=foo}"
	 * MoreObjects.toStringHelper(this)
	 * .add("x", 1)
	 * .add("y", "foo")
	 * .toString();
	 *
	 * // Returns "ClassName{x=1}"
	 * MoreObjects.toStringHelper(this)
	 * .omitNullValues()
	 * .add("x", 1)
	 * .add("y", null)
	 * .toString();
	 * }
	 *
	 * <p>
	 * Note that in GWT, class names are often obfuscated.
	 *
	 * @param self the object to generate the string for (typically {@code this}), used only for its
	 *             class name
	 * @since 18.0 (since 2.0 as {@code Objects.toStringHelper()}).
	 */
	public static ToStringHelper toStringHelper(Object self) {
		return new ToStringHelper(
			self.getClass()
				.getSimpleName());
	}

	/**
	 * Support class for {@link MoreObjects#toStringHelper}.
	 *
	 * @author Jason Lee
	 * @since 18.0 (since 2.0 as {@code Objects.ToStringHelper}).
	 */
	public static final class ToStringHelper {
		
		private final String className;
		private final ValueHolder holderHead = new ValueHolder();
		private ValueHolder holderTail = holderHead;
		private boolean omitNullValues = false;
		private boolean omitEmptyValues = false;
		
		/** Use {@link MoreObjects#toStringHelper(Object)} to create an instance. */
		private ToStringHelper(String className) {
			this.className = checkNotNull(className);
		}

		/**
		 * Adds a name/value pair to the formatted output in {@code name=value} format. If {@code value}
		 * is {@code null}, the string {@code "null"} is used.
		 */
		
		public ToStringHelper add(String name, Object value) {
			return addHolder(name, value);
		}
		
		/**
		 * Adds a name/value pair to the formatted output in {@code name=value} format.
		 *
		 * @since 18.0 (since 11.0 as {@code Objects.ToStringHelper.add()}).
		 */
		
		public ToStringHelper add(String name, boolean value) {
			return addUnconditionalHolder(name, String.valueOf(value));
		}
		
		/**
		 * Adds a name/value pair to the formatted output in {@code name=value} format.
		 *
		 * @since 18.0 (since 11.0 as {@code Objects.ToStringHelper.add()}).
		 */
		
		public ToStringHelper add(String name, int value) {
			return addUnconditionalHolder(name, String.valueOf(value));
		}
		
		/**
		 * Adds a name/value pair to the formatted output in {@code name=value} format.
		 *
		 * @since 18.0 (since 11.0 as {@code Objects.ToStringHelper.add()}).
		 */
		
		public ToStringHelper add(String name, long value) {
			return addUnconditionalHolder(name, String.valueOf(value));
		}

		private static boolean isEmpty(Object value) {
			// Put types estimated to be the most frequent first.
			if (value instanceof CharSequence) {
				return ((CharSequence) value).length() == 0;
			} else if (value instanceof Collection) {
				return ((Collection<?>) value).isEmpty();
			} else if (value instanceof Map) {
				return ((Map<?, ?>) value).isEmpty();
			} else if (value instanceof java.util.Optional) {
				return !((java.util.Optional<?>) value).isPresent();
			} else if (value instanceof OptionalInt) {
				return !((OptionalInt) value).isPresent();
			} else if (value instanceof OptionalLong) {
				return !((OptionalLong) value).isPresent();
			} else if (value instanceof OptionalDouble) {
				return !((OptionalDouble) value).isPresent();
			} else if (value instanceof Optional) {
				return !((Optional) value).isPresent();
			} else if (value.getClass()
				.isArray()) {
				return Array.getLength(value) == 0;
			}
			return false;
		}
		
		/**
		 * Returns a string in the format specified by {@link MoreObjects#toStringHelper(Object)}.
		 *
		 * <p>
		 * After calling this method, you can keep adding more properties to later call toString()
		 * again and get a more complete representation of the same object; but properties cannot be
		 * removed, so this only allows limited reuse of the helper instance. The helper allows
		 * duplication of properties (multiple name/value pairs with the same name can be added).
		 */
		@Override
		public String toString() {
			// create a copy to keep it consistent in case value changes
			boolean omitNullValuesSnapshot = omitNullValues;
			boolean omitEmptyValuesSnapshot = omitEmptyValues;
			String nextSeparator = "";
			StringBuilder builder = new StringBuilder(32).append(className)
				.append('{');
			for (ValueHolder valueHolder = holderHead.next; valueHolder != null; valueHolder = valueHolder.next) {
				Object value = valueHolder.value;
				if (valueHolder instanceof UnconditionalValueHolder
					|| (value == null ? !omitNullValuesSnapshot : (!omitEmptyValuesSnapshot || !isEmpty(value)))) {
					builder.append(nextSeparator);
					nextSeparator = ", ";
					
					if (valueHolder.name != null) {
						builder.append(valueHolder.name)
							.append('=');
					}
					if (value != null && value.getClass()
						.isArray()) {
						Object[] objectArray = { value };
						String arrayString = Arrays.deepToString(objectArray);
						builder.append(arrayString, 1, arrayString.length() - 1);
					} else {
						builder.append(value);
					}
				}
			}
			return builder.append('}')
				.toString();
		}
		
		private ValueHolder addHolder() {
			ValueHolder valueHolder = new ValueHolder();
			holderTail = holderTail.next = valueHolder;
			return valueHolder;
		}
		
		private ToStringHelper addHolder(String name, Object value) {
			ValueHolder valueHolder = addHolder();
			valueHolder.value = value;
			valueHolder.name = checkNotNull(name);
			return this;
		}
		
		private UnconditionalValueHolder addUnconditionalHolder() {
			UnconditionalValueHolder valueHolder = new UnconditionalValueHolder();
			holderTail = holderTail.next = valueHolder;
			return valueHolder;
		}
		
		private ToStringHelper addUnconditionalHolder(String name, Object value) {
			UnconditionalValueHolder valueHolder = addUnconditionalHolder();
			valueHolder.value = value;
			valueHolder.name = checkNotNull(name);
			return this;
		}
		
		// Holder object for values that might be null and/or empty.
		static class ValueHolder {
			
			String name;
			Object value;
			ValueHolder next;
		}
		
		/**
		 * Holder object for values that cannot be null or empty (will be printed unconditionally). This
		 * helps to shortcut most calls to isEmpty(), which is important because the check for emptiness
		 * is relatively expensive. Use a subtype so this also doesn't need any extra storage.
		 */
		private static final class UnconditionalValueHolder extends ValueHolder {
		}
	}
	
	private MoreObjects() {}
}
