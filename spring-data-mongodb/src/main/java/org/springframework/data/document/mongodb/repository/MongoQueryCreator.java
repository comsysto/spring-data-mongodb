/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.document.mongodb.repository;

import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.data.document.mongodb.MongoConverter;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.SimpleParameterAccessor;
import org.springframework.data.repository.query.SimpleParameterAccessor.BindableParameterIterator;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.Part.Type;
import org.springframework.data.repository.query.parser.PartTree;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;


/**
 * Custom query creator to create Mongo criterias.
 * 
 * @author Oliver Gierke
 */
class MongoQueryCreator extends AbstractQueryCreator<DBObject, QueryBuilder> {
	
	private static final Log LOG = LogFactory.getLog(MongoQueryCreator.class);
	private final MongoConverter converter;

    /**
     * Creates a new {@link MongoQueryCreator} from the given {@link PartTree}
     * and {@link SimpleParameterAccessor}.
     * 
     * @param tree
     * @param accessor
     */
    public MongoQueryCreator(PartTree tree, SimpleParameterAccessor accessor, MongoConverter converter) {

        super(tree, accessor);
        this.converter = converter;
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.data.document.mongodb.repository.AbstractQueryCreator
     * #create(org.springframework.data.repository.query.parser.Part,
     * org.springframework
     * .data.repository.query.SimpleParameterAccessor.BindableParameterIterator)
     */
    @Override
    protected QueryBuilder create(Part part, BindableParameterIterator iterator) {

        return from(part.getType(), QueryBuilder.start(part.getProperty().toDotPath()),
                iterator);
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.data.document.mongodb.repository.AbstractQueryCreator
     * #handlePart(org.springframework.data.repository.query.parser.Part,
     * org.springframework
     * .data.repository.query.SimpleParameterAccessor.BindableParameterIterator)
     */
    @Override
    protected QueryBuilder and(Part part, QueryBuilder base,
            BindableParameterIterator iterator) {

        return from(part.getType(), base.and(part.getProperty().toDotPath()), iterator);
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.data.document.mongodb.repository.AbstractQueryCreator
     * #reduceCriterias(java.lang.Object, java.lang.Object)
     */
    @Override
    protected QueryBuilder or(QueryBuilder base, QueryBuilder criteria) {

        base.or(criteria.get());
        return base;
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.data.document.mongodb.repository.AbstractQueryCreator
     * #finalize(java.lang.Object)
     */
    @Override
    protected DBObject complete(QueryBuilder criteria, Sort sort) {

    	DBObject query = criteria.get();
    	
    	if (LOG.isDebugEnabled()) {
    		LOG.debug("Created query " + query);
    	}
    	
        return query;
    }


    /**
     * Populates the given {@link Criteria} depending on the {@link Type} given.
     * 
     * @param type
     * @param criteria
     * @param parameters
     * @return
     */
    private QueryBuilder from(Type type, QueryBuilder criteria,
            BindableParameterIterator parameters) {

        switch (type) {
        case GREATER_THAN:
            return criteria.greaterThan(getConvertedParameter(parameters));
        case LESS_THAN:
            return criteria.lessThan(getConvertedParameter(parameters));
        case BETWEEN:
            return criteria.greaterThan(getConvertedParameter(parameters)).lessThan(getConvertedParameter(parameters));
        case IS_NOT_NULL:
            return criteria.notEquals(null);
        case IS_NULL:
            return criteria.is(null);
        case LIKE:
            String value = parameters.next().toString();
            return criteria.regex(toLikeRegex(value));
        case SIMPLE_PROPERTY:
            return criteria.is(getConvertedParameter(parameters));
        case NEGATING_SIMPLE_PROPERTY:
            return criteria.notEquals(getConvertedParameter(parameters));
        }

        throw new IllegalArgumentException("Unsupported keyword!");
    }
    
    
    private Object getConvertedParameter(BindableParameterIterator parameters) {
    	
    	DBObject result = new BasicDBObject();
    	converter.write(new ValueHolder(parameters.next()), result);
    	return result.get("value");
    }


    private Pattern toLikeRegex(String source) {

        String regex = source.replaceAll("\\*", ".*");
        return Pattern.compile(regex);
    }

	/**
	 * Simple value holder class to allow conversion and accessing the converted value in a deterministic way.
	 * 
	 * @author Oliver Gierke
	 */
    private static class ValueHolder {
    	
    	private Object value;
    	
    	public ValueHolder(Object value) {
    		this.value = value;
    	}
    	
    	@SuppressWarnings("unused")
		public Object getValue() {
			return value;
		}
    }
}