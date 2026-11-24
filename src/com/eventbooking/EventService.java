
package com.eventbooking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;

public class EventService {

    public boolean addEvent(String eventName, Date eventDate,
                            String venue, double ticketPrice,
                            int availableTickets) throws SQLException {

        String sql = "INSERT INTO events " +
                "(event_name, event_date, venue, ticket_price, available_tickets) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, eventName);
            ps.setDate(2, eventDate);
            ps.setString(3, venue);
            ps.setDouble(4, ticketPrice);
            ps.setInt(5, availableTickets);

            return ps.executeUpdate() > 0;
        }
    }

    public void viewEvents() throws SQLException {

        String sql = "SELECT * FROM events ORDER BY event_date";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("\n========== AVAILABLE EVENTS ==========");

            boolean found = false;

            while (rs.next()) {
                found = true;

                System.out.println("Event ID: " +
                        rs.getInt("event_id"));
                System.out.println("Name: " +
                        rs.getString("event_name"));
                System.out.println("Date: " +
                        rs.getDate("event_date"));
                System.out.println("Venue: " +
                        rs.getString("venue"));
                System.out.println("Ticket Price: ₹" +
                        rs.getBigDecimal("ticket_price"));
                System.out.println("Available Tickets: " +
                        rs.getInt("available_tickets"));

                System.out.println("--------------------------------------");
            }

            if (!found) {
                System.out.println("No events available yet.");
            }
        }
    }
}
