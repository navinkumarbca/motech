package org.motechproject.mds.rest;

import org.motechproject.mds.query.QueryParams;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Interface called by the REST controller REST operations.
 * Should be exposed as an OSGi service for each MDS Entity.
 * If rest is not supported, it throws a {@link org.motechproject.mds.ex.rest.RestNotSupportedException}.
 *
 * @param <T> the entity class.
 */
public interface MdsRestFacade<T> {

    List<T> get(QueryParams queryParams);

    void create(InputStream instanceBody);

    void update(InputStream instanceBody);

    void delete(Long id);

    Object executeLookup(String lookupName, Map<String, Object> lookupMap);
}