/*
 * Copyright 2011- Per Wendel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package spark.http.matching;

import spark.Access;
import spark.QueryParamsMap;
import spark.Request;
import spark.Session;
import spark.routematch.RouteMatch;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;

final class RequestWrapper extends Request {

    private Request delegate;

    private RequestWrapper() {
        // hidden
    }

    @Override
    public void attribute(String attribute, Object value) {
        delegate.attribute(attribute, value);
    }

    @Override
    public <T> T attribute(String attribute) {
        return delegate.attribute(attribute);
    }

    @Override
    public Set<String> attributes() {
        return delegate.attributes();
    }

    @Override
    public String body() {
        return delegate.body();
    }

    @Override
    public byte[] bodyAsBytes() {
        return delegate.bodyAsBytes();
    }

    public void changeMatch(RouteMatch match) {
        Access.changeMatch(delegate, match);
    }

    @Override
    public int contentLength() {
        return delegate.contentLength();
    }

    @Override
    public String contentType() {
        return delegate.contentType();
    }

    @Override
    public String contextPath() {
        return delegate.contextPath();
    }

    @Override
    public String cookie(String name) {
        return delegate.cookie(name);
    }

    @Override
    public Map<String, String> cookies() {
        return delegate.cookies();
    }

    static RequestWrapper create() {
        return new RequestWrapper();
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    Request getDelegate() {
        return delegate;
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String headers(String header) {
        return delegate.headers(header);
    }

    @Override
    public Set<String> headers() {
        return delegate.headers();
    }

    @Override
    public String host() {
        return delegate.host();
    }

    @Override
    public String ip() {
        return delegate.ip();
    }

    @Override
    public String matchedPath() {
        return delegate.matchedPath();
    }

    @Override
    public Map<String, String> params() {
        return delegate.params();
    }

    @Override
    public String params(String param) {
        return delegate.params(param);
    }

    @Override
    public String pathInfo() {
        return delegate.pathInfo();
    }

    @Override
    public int port() {
        return delegate.port();
    }

    @Override
    public String protocol() {
        return delegate.protocol();
    }

    @Override
    public QueryParamsMap queryMap() {
        return delegate.queryMap();
    }

    @Override
    public QueryParamsMap queryMap(String key) {
        return delegate.queryMap(key);
    }

    @Override
    public String queryParams(String queryParam) {
        return delegate.queryParams(queryParam);
    }

    @Override
    public Set<String> queryParams() {
        return delegate.queryParams();
    }

    @Override
    public String queryParamsSafe(String queryParam) {
        return delegate.queryParams(queryParam);
    }

    @Override
    public String[] queryParamsValues(String queryParam) {
        return delegate.queryParamsValues(queryParam);
    }

    @Override
    public String queryString() {
        return delegate.queryString();
    }

    @Override
    public HttpServletRequest raw() {
        return delegate.raw();
    }

    @Override
    public String requestMethod() {
        return delegate.requestMethod();
    }

    @Override
    public String scheme() {
        return delegate.scheme();
    }

    @Override
    public String servletPath() {
        return delegate.servletPath();
    }

    @Override
    public Session session() {
        return delegate.session();
    }

    @Override
    public Session session(boolean create) {
        return delegate.session(create);
    }

    public void setDelegate(Request delegate) {
        this.delegate = delegate;
    }

    @Override
    public String[] splat() {
        return delegate.splat();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public String uri() {
        return delegate.uri();
    }

    @Override
    public String url() {
        return delegate.url();
    }

    @Override
    public String userAgent() {
        return delegate.userAgent();
    }
}
