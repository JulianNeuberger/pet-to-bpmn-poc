package http;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

public class CorsFilter extends Filter {
    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        System.out.println("Cors filter");
        try {
            String origin = exchange.getRequestHeaders().getFirst("origin");
            if(origin == null) {
                origin = "*";
            }

            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Headers", "Content-Type");
            headers.add("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE");
            headers.add("Access-Control-Allow-Origin", origin);
            headers.add("Access-Control-Max-Age", "3600");

            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
            } else {
                chain.doFilter(exchange);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String description() {
        return null;
    }
}