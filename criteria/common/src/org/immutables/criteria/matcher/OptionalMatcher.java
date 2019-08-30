/*
 * Copyright 2019 Immutables Authors and Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.immutables.criteria.matcher;

import java.util.Optional;

/**
 * Matcher for optional attributes
 */
public interface OptionalMatcher<R, S, V> extends PresentAbsentMatcher<R>, Matcher {

  /**
   * Apply context-specific matcher if value is present
   */
  default S value() {
    // CriteriaContext.this.creator;
    return Matchers.extract(this).<S>create();
  }

  /**
   * Self-type for this matcher
   */
  interface Self<S, V> extends Template<Self<S, V>, S, V>, Disjunction<Self<S, V>> {}

  interface Template<R, S, V> extends OptionalMatcher<R, S, V>, Projection<Optional<V>>, AggregationTemplate<V> {}

  interface AggregationTemplate<V> extends Aggregation.Count {}

  @SuppressWarnings("unchecked")
  static <R> CriteriaCreator<R> creator() {
    class Local extends AbstractContextHolder implements Self {
      private Local(CriteriaContext context) {
        super(context);
      }
    }

    return ctx -> (R) new Local(ctx);
  }


}
