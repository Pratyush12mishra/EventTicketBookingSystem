
package com.eventbooking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthService {

    public boolean register(String username, String password, Role role)
            throws SQLException {

        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, role.name());

            return ps.executeUpdate() > 0;
        }
    }

    public User login(String username, String password)
            throws SQLException {

        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        Role.valueOf(rs.getString("role"))
                    );
                }
            }
        }

        return null;
    }
}
