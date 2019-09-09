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

package org.immutables.criteria.repository.reactive;

import org.immutables.criteria.backend.Backend;
import org.immutables.criteria.backend.NonUniqueResultException;
import org.immutables.criteria.backend.ProjectedTuple;
import org.immutables.criteria.backend.StandardOperations;
import org.immutables.criteria.expression.Query;
import org.immutables.criteria.matcher.Matchers;
import org.immutables.criteria.matcher.Projection;
import org.immutables.criteria.repository.Publishers;
import org.immutables.criteria.repository.Tuple;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

class ReactiveFetcherDelegate<T> implements ReactiveFetcher<T> {

  private final Query query;
  private final Backend.Session session;

  ReactiveFetcherDelegate(Query query, Backend.Session session) {
    this.query = Objects.requireNonNull(query, "query");
    this.session = Objects.requireNonNull(session, "session");
  }

  @Override
  public Publisher<T> fetch() {
    return session.execute(StandardOperations.Select.of(query)).publisher();
  }

  @Override
  public Publisher<T> one() {
    Consumer<List<T>> checkFn = list -> {
      if (list.size() != 1) {
         throw new NonUniqueResultException(String.format("Expected exactly one element but got %d for query %s", list.size(), query));
      }
    };

    return validateAsList(checkFn);
  }

  @Override
  public Publisher<T> oneOrNone() {
    Consumer<List<T>> checkFn = list -> {
      if (list.size() > 1) {
        throw new NonUniqueResultException(String.format("Expected at most one element but got (at least) %d for query %s", list.size(), query));
      }
    };

    return validateAsList(checkFn);
  }

  private Publisher<T> validateAsList(Consumer<List<T>> validatorFn) {
    Query query = this.query;
    if (!query.limit().isPresent()) {
      // ensure at most one element
      // fail if there are 2 or more
      query = query.withLimit(2);
    }

    Publisher<List<T>> asList = Publishers.toList(session.execute(StandardOperations.Select.of(query)).publisher());
    Function<List<T>, List<T>> mapFn = list -> {
      validatorFn.accept(list);
      return list;
    };
    return Publishers.flatMapIterable(Publishers.map(asList, mapFn), x -> x);
  }

  @Override
  public Publisher<Boolean> exists() {
    Query query = this.query.withLimit(1); // check if at least one exists
    Publisher<List<T>> asList = Publishers.toList(session.execute(StandardOperations.Select.of(query)).publisher());
    return Publishers.map(asList, list -> !list.isEmpty());
  }

  @Override
  public <X> ReactiveFetcher<X> map(Function<? super T, ? extends X> mapFn) {
    return new MappedFetcher<T, X>(query, session, mapFn);
  }

  private static class MappedFetcher<T, R> extends ReactiveFetcherDelegate<R> {

    private final Function<? super T, ? extends R> mapFn;

    private MappedFetcher(Query query, Backend.Session session, Function<? super T, ? extends R> mapFn) {
      super(query, session);
      Objects.requireNonNull(mapFn, "mapFn");
      if (query.hasProjections()) {
        // expect ProjectedTuple from backend
        @SuppressWarnings("unchecked")
        Function<T, T> newMapFn = tuple -> (T) new ProjectedTupleAdapter((ProjectedTuple) tuple);
        this.mapFn = mapFn.compose(newMapFn);
      } else {
        this.mapFn = mapFn;
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Publisher<R> fetch() {
      return Publishers.map((Publisher<T>) super.fetch(), mapFn);
    }

  }

  /**
   * Used to convert backend {@link ProjectedTuple} into repository specific {@link Tuple} interface
   */
  private static class ProjectedTupleAdapter implements Tuple {

    private final ProjectedTuple delegate;

    private ProjectedTupleAdapter(ProjectedTuple delegate) {
      this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Projection<T> projection) {
      return (T) delegate.get(Matchers.toExpression(projection));
    }

    @Override
    public List<?> values() {
      return delegate.values();
    }
  }

}