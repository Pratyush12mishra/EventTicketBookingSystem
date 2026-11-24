
package com.eventbooking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BookingService {

    public boolean bookTickets(int userId, int eventId, int quantity)
            throws SQLException {

        if (quantity <= 0) {
            System.out.println("Enter a valid ticket quantity.");
            return false;
        }

        try (Connection con = DBConnection.getConnection()) {

            con.setAutoCommit(false);

            try {
                String checkSql =
                    "SELECT available_tickets FROM events " +
                    "WHERE event_id = ? FOR UPDATE";

                int availableTickets;

                try (PreparedStatement ps = con.prepareStatement(checkSql)) {
                    ps.setInt(1, eventId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            System.out.println("Event not found.");
                            con.rollback();
                            return false;
                        }

                        availableTickets = rs.getInt("available_tickets");
                    }
                }

                if (availableTickets < quantity) {
                    System.out.println("Not enough tickets available.");
                    con.rollback();
                    return false;
                }

                String updateSql =
                    "UPDATE events SET available_tickets = " +
                    "available_tickets - ? WHERE event_id = ?";

                try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                    ps.setInt(1, quantity);
                    ps.setInt(2, eventId);
                    ps.executeUpdate();
                }

                String insertSql =
                    "INSERT INTO bookings " +
                    "(user_id, event_id, tickets_booked) VALUES (?, ?, ?)";

                try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, eventId);
                    ps.setInt(3, quantity);
                    ps.executeUpdate();
                }

                con.commit();
                System.out.println("Tickets booked successfully!");
                return true;

            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public void viewMyBookings(int userId) throws SQLException {

        String sql =
            "SELECT b.booking_id, e.event_name, e.event_date, " +
            "b.tickets_booked, e.ticket_price " +
            "FROM bookings b " +
            "JOIN events e ON b.event_id = e.event_id " +
            "WHERE b.user_id = ? " +
            "ORDER BY b.booking_date DESC";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {

                System.out.println("\n========== MY BOOKINGS ==========");
                boolean found = false;

                while (rs.next()) {
                    found = true;

                    System.out.println(
                        "Booking ID: " + rs.getInt("booking_id") +
                        " | Event: " + rs.getString("event_name") +
                        " | Date: " + rs.getDate("event_date") +
                        " | Tickets: " + rs.getInt("tickets_booked") +
                        " | Price per ticket: ₹" +
                        rs.getBigDecimal("ticket_price")
                    );
                }

                if (!found) {
                    System.out.println("You have no bookings yet.");
                }
            }
        }
    }
}
