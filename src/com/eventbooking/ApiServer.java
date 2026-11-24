
package com.eventbooking;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiServer {

    private static final AuthService authService = new AuthService();
    private static final BookingService bookingService = new BookingService();

    public static void main(String[] args) throws IOException {

        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080), 0);

        server.createContext("/", ApiServer::handleRequest);
        server.start();

        System.out.println(
                "Evently API running at http://localhost:8080");
    }

    private static void handleRequest(HttpExchange exchange)
            throws IOException {

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {

            // GET: Retrieve all events
            if (path.equals("/api/events") && method.equals("GET")) {

                send(exchange, 200, getEvents());
            }

            // POST: Create a new event
            else if (path.equals("/api/events")
                    && method.equals("POST")) {

                Map<String, String> data = readJson(exchange);

                String name = required(data, "name");
                String dateText = required(data, "date");
                String venue = required(data, "venue");

                BigDecimal price = new BigDecimal(
                        required(data, "price"));

                int tickets = Integer.parseInt(
                        required(data, "availableTickets"));

                if (price.compareTo(BigDecimal.ZERO) <= 0
                        || tickets <= 0) {

                    send(exchange, 400,
                            "{\"success\":false,"
                            + "\"message\":\"Price and ticket quantity "
                            + "must be positive.\"}");
                    return;
                }

                Date eventDate = Date.valueOf(dateText);

                String sql =
                        "INSERT INTO events "
                        + "(event_name, event_date, venue, "
                        + "ticket_price, available_tickets) "
                        + "VALUES (?, ?, ?, ?, ?)";

                try (Connection con = DBConnection.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {

                    ps.setString(1, name);
                    ps.setDate(2, eventDate);
                    ps.setString(3, venue);
                    ps.setBigDecimal(4, price);
                    ps.setInt(5, tickets);

                    ps.executeUpdate();
                }

                send(exchange, 201,
                        "{\"success\":true,"
                        + "\"message\":\"Event created successfully.\"}");
            }

            // POST: Register a customer
            else if (path.equals("/api/register")
                    && method.equals("POST")) {

                Map<String, String> data = readForm(exchange);

                String username = required(data, "username");
                String password = required(data, "password");

                boolean registered = authService.register(
                        username, password, Role.CUSTOMER);

                send(exchange, registered ? 201 : 400,
                        "{\"success\":" + registered
                        + ",\"message\":\""
                        + (registered
                           ? "Registration completed"
                           : "Registration failed. Username may already exist.")
                        + "\"}");
            }

            // POST: Login
            else if (path.equals("/api/login")
                    && method.equals("POST")) {

                Map<String, String> data = readForm(exchange);

                User user = authService.login(
                        data.get("username"),
                        data.get("password"));

                if (user == null) {

                    send(exchange, 401,
                            "{\"success\":false,"
                            + "\"message\":\"Invalid username or password\"}");

                } else {

                    send(exchange, 200,
                            "{\"success\":true,"
                            + "\"userId\":" + user.getUserId()
                            + ",\"username\":\""
                            + escape(user.getUsername())
                            + "\",\"role\":\""
                            + user.getRole().name() + "\"}");
                }
            }

            // POST: Book tickets
            else if (path.equals("/api/bookings")
                    && method.equals("POST")) {

                Map<String, String> data = readForm(exchange);

                int userId = Integer.parseInt(
                        required(data, "userId"));

                int eventId = Integer.parseInt(
                        required(data, "eventId"));

                int quantity = Integer.parseInt(
                        required(data, "quantity"));

                if (quantity <= 0) {
                    send(exchange, 400,
                            "{\"success\":false,"
                            + "\"message\":\"Quantity must be positive.\"}");
                    return;
                }

                // Ensure the user exists before booking.
                if (!userExists(userId)) {
                    send(exchange, 403,
                            "{\"success\":false,"
                            + "\"message\":\"User not found.\"}");
                    return;
                }

                boolean booked = bookingService.bookTickets(
                        userId, eventId, quantity);

                send(exchange, booked ? 200 : 400,
                        "{\"success\":" + booked
                        + ",\"message\":\""
                        + (booked
                           ? "Tickets booked successfully."
                           : "Booking failed. Check ticket availability.")
                        + "\"}");
            }

            // GET: View bookings for a customer
            // Example: /api/bookings?userId=2
            else if (path.equals("/api/bookings")
                    && method.equals("GET")) {

                int userId = Integer.parseInt(
                        requiredQueryParam(
                                exchange, "userId"));

                if (!userExists(userId)) {
                    send(exchange, 404,
                            "{\"success\":false,"
                            + "\"message\":\"User not found.\"}");
                    return;
                }

                send(exchange, 200, getBookings(userId, false));
            }

            // GET: Admin views all bookings
            // Example: /api/admin/bookings?userId=1
            else if (path.equals("/api/admin/bookings")
                    && method.equals("GET")) {

                int userId = Integer.parseInt(
                        requiredQueryParam(
                                exchange, "userId"));

                if (!userHasRole(userId, "ADMIN")) {
                    send(exchange, 403,
                            "{\"success\":false,"
                            + "\"message\":\"Admin access required.\"}");
                    return;
                }

                send(exchange, 200, getBookings(userId, true));
            }

            // GET: Admin sees tickets sold and remaining
            // Example: /api/admin/summary?userId=1
            else if (path.equals("/api/admin/summary")
                    && method.equals("GET")) {

                int userId = Integer.parseInt(
                        requiredQueryParam(
                                exchange, "userId"));

                if (!userHasRole(userId, "ADMIN")) {
                    send(exchange, 403,
                            "{\"success\":false,"
                            + "\"message\":\"Admin access required.\"}");
                    return;
                }

                send(exchange, 200, getAdminSummary());
            }

            // Unknown endpoint
            else {
                send(exchange, 404,
                        "{\"success\":false,"
                        + "\"message\":\"API endpoint not found\"}");
            }

        } catch (SQLException | IllegalArgumentException e) {

            send(exchange, 400,
                    "{\"success\":false,\"message\":\""
                    + escape(e.getMessage() == null
                             ? "Invalid request" : e.getMessage())
                    + "\"}");

        } catch (Exception e) {

            e.printStackTrace();

            send(exchange, 500,
                    "{\"success\":false,"
                    + "\"message\":\"Server error\"}");
        }
    }

    // Retrieve all events from MySQL.
    private static String getEvents() throws SQLException {

        String sql =
                "SELECT event_id, event_name, event_date, venue, "
                + "ticket_price, available_tickets "
                + "FROM events ORDER BY event_date";

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                if (!first) {
                    json.append(",");
                }

                first = false;

                json.append("{")
                    .append("\"id\":")
                    .append(rs.getInt("event_id"))
                    .append(",\"name\":\"")
                    .append(escape(rs.getString("event_name")))
                    .append("\",\"date\":\"")
                    .append(rs.getDate("event_date"))
                    .append("\",\"venue\":\"")
                    .append(escape(rs.getString("venue")))
                    .append("\",\"price\":")
                    .append(rs.getBigDecimal("ticket_price"))
                    .append(",\"availableTickets\":")
                    .append(rs.getInt("available_tickets"))
                    .append("}");
            }
        }

        return json.append("]").toString();
    }

    // Check whether a user ID exists.
    private static boolean userExists(int userId)
            throws SQLException {

        String sql = "SELECT 1 FROM users WHERE user_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // Check the role stored in the database.
    private static boolean userHasRole(int userId, String role)
            throws SQLException {

        String sql =
                "SELECT 1 FROM users "
                + "WHERE user_id = ? AND role = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setString(2, role);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // Retrieve customer bookings or all bookings for an admin.
    private static String getBookings(int userId, boolean all)
            throws SQLException {

        String sql =
                "SELECT b.booking_id, u.username, e.event_name, "
                + "e.event_date, e.venue, b.tickets_booked, "
                + "e.ticket_price, b.booking_date "
                + "FROM bookings b "
                + "JOIN users u ON b.user_id = u.user_id "
                + "JOIN events e ON b.event_id = e.event_id "
                + (all ? "" : "WHERE b.user_id = ? ")
                + "ORDER BY b.booking_date DESC";

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            if (!all) {
                ps.setInt(1, userId);
            }

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    if (!first) {
                        json.append(",");
                    }

                    first = false;

                    int quantity = rs.getInt("tickets_booked");
                    BigDecimal price =
                            rs.getBigDecimal("ticket_price");

                    BigDecimal total =
                            price.multiply(BigDecimal.valueOf(quantity));

                    json.append("{")
                        .append("\"bookingId\":")
                        .append(rs.getInt("booking_id"))
                        .append(",\"username\":\"")
                        .append(escape(rs.getString("username")))
                        .append("\",\"eventName\":\"")
                        .append(escape(rs.getString("event_name")))
                        .append("\",\"eventDate\":\"")
                        .append(rs.getDate("event_date"))
                        .append("\",\"venue\":\"")
                        .append(escape(rs.getString("venue")))
                        .append("\",\"ticketsBooked\":")
                        .append(quantity)
                        .append(",\"pricePerTicket\":")
                        .append(price)
                        .append(",\"totalAmount\":")
                        .append(total)
                        .append(",\"bookingDate\":\"")
                        .append(escape(
                                String.valueOf(
                                        rs.getTimestamp("booking_date"))))
                        .append("\"}");
                }
            }
        }

        return json.append("]").toString();
    }

    // Summarize ticket sales and remaining tickets for each event.
    private static String getAdminSummary() throws SQLException {

        String sql =
                "SELECT e.event_id, e.event_name, "
                + "e.available_tickets, "
                + "COALESCE(SUM(b.tickets_booked), 0) AS tickets_sold "
                + "FROM events e "
                + "LEFT JOIN bookings b ON e.event_id = b.event_id "
                + "GROUP BY e.event_id, e.event_name, "
                + "e.available_tickets "
                + "ORDER BY e.event_date";

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                if (!first) {
                    json.append(",");
                }

                first = false;

                json.append("{")
                    .append("\"eventId\":")
                    .append(rs.getInt("event_id"))
                    .append(",\"eventName\":\"")
                    .append(escape(rs.getString("event_name")))
                    .append("\",\"ticketsSold\":")
                    .append(rs.getInt("tickets_sold"))
                    .append(",\"ticketsRemaining\":")
                    .append(rs.getInt("available_tickets"))
                    .append("}");
            }
        }

        return json.append("]").toString();
    }

    // Read the userId query parameter.
    private static String requiredQueryParam(
            HttpExchange exchange, String key) {

        String query = exchange.getRequestURI().getRawQuery();

        if (query == null) {
            throw new IllegalArgumentException(
                    "Missing query parameter: " + key);
        }

        for (String pair : query.split("&")) {

            String[] parts = pair.split("=", 2);

            String name = URLDecoder.decode(
                    parts[0], StandardCharsets.UTF_8);

            if (name.equals(key)
                    && parts.length == 2
                    && !parts[1].isBlank()) {

                return URLDecoder.decode(
                        parts[1], StandardCharsets.UTF_8);
            }
        }

        throw new IllegalArgumentException(
                "Missing query parameter: " + key);
    }

    // Read a flat JSON object sent by the frontend.
    private static Map<String, String> readJson(
            HttpExchange exchange) throws IOException {

        String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        Map<String, String> data = new HashMap<>();

        Pattern pattern = Pattern.compile(
                "\"([^\"]+)\"\\s*:\\s*"
                + "(?:\"((?:\\\\.|[^\"])*)\"|([^,}\\s]+))");

        Matcher matcher = pattern.matcher(body);

        while (matcher.find()) {

            String value = matcher.group(2) != null
                    ? matcher.group(2)
                    : matcher.group(3);

            if (value != null) {

                value = value.replace("\\\"", "\"")
                             .replace("\\\\", "\\");

                data.put(matcher.group(1), value);
            }
        }

        return data;
    }

    // Get a required field from a request.
    private static String required(
            Map<String, String> data, String key) {

        String value = data.get(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required field: " + key);
        }

        return value.trim();
    }

    // Read URL-encoded form data.
    private static Map<String, String> readForm(
            HttpExchange exchange) throws IOException {

        String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        Map<String, String> data = new HashMap<>();

        for (String pair : body.split("&")) {

            String[] parts = pair.split("=", 2);

            String key = URLDecoder.decode(
                    parts[0], StandardCharsets.UTF_8);

            String value = parts.length > 1
                    ? URLDecoder.decode(
                            parts[1], StandardCharsets.UTF_8)
                    : "";

            data.put(key, value);
        }

        return data;
    }

    // Escape values before inserting them into JSON responses.
    private static String escape(String value) {

        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    // Send a JSON response to the frontend.
    private static void send(
            HttpExchange exchange, int status, String body)
            throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");

        exchange.sendResponseHeaders(status, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
