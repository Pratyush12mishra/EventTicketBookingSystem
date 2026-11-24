
package com.eventbooking;

import java.sql.Date;
import java.sql.SQLException;
import java.util.Scanner;

public class MainApplication {

    static Scanner sc = new Scanner(System.in);

    static AuthService authService = new AuthService();
    static EventService eventService = new EventService();
    static BookingService bookingService = new BookingService();

    public static void main(String[] args) {

        int choice;

        do {
            System.out.println("\n======================================");
            System.out.println(" EVENT REGISTRATION & TICKET BOOKING ");
            System.out.println("======================================");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Exit");

            choice = readInt("Enter your choice: ");

            switch (choice) {
                case 1:
                    login();
                    break;

                case 2:
                    register();
                    break;

                case 3:
                    System.out.println("Thank you for using our application!");
                    break;

                default:
                    System.out.println("Invalid choice. Try again.");
            }

        } while (choice != 3);

        sc.close();
    }

    static void register() {
        System.out.println("\n========== CUSTOMER REGISTRATION ==========");

        System.out.print("Enter username: ");
        String username = sc.nextLine().trim();

        System.out.print("Enter password: ");
        String password = sc.nextLine();

        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("Username and password cannot be empty.");
            return;
        }

        try {
            boolean registered =
                    authService.register(username, password, Role.CUSTOMER);

            if (registered) {
                System.out.println("Registration successful! You can now log in.");
            } else {
                System.out.println("Registration failed.");
            }

        } catch (SQLException e) {
            System.out.println("Registration failed. Username may already exist.");
            System.out.println("Details: " + e.getMessage());
        }
    }

    static void login() {
        System.out.println("\n========== LOGIN ==========");

        System.out.print("Enter username: ");
        String username = sc.nextLine().trim();

        System.out.print("Enter password: ");
        String password = sc.nextLine();

        try {
            User user = authService.login(username, password);

            if (user == null) {
                System.out.println("Invalid username or password.");
                return;
            }

            System.out.println("Login successful! Welcome, " + user.getUsername());

            if (user.getRole() == Role.ADMIN) {
                adminMenu();
            } else {
                customerMenu(user);
            }

        } catch (SQLException e) {
            System.out.println("Login failed: " + e.getMessage());
        }
    }

    static void adminMenu() {
        int choice;

        do {
            System.out.println("\n========== ADMIN MENU ==========");
            System.out.println("1. Add Event");
            System.out.println("2. View Events");
            System.out.println("3. Logout");

            choice = readInt("Enter your choice: ");

            switch (choice) {
                case 1:
                    addEvent();
                    break;

                case 2:
                    viewEvents();
                    break;

                case 3:
                    System.out.println("Admin logged out.");
                    break;

                default:
                    System.out.println("Invalid choice.");
            }

        } while (choice != 3);
    }

    static void addEvent() {
        try {
            System.out.println("\n========== ADD EVENT ==========");

            System.out.print("Enter event name: ");
            String name = sc.nextLine().trim();

            System.out.print("Enter event date (YYYY-MM-DD): ");
            Date date = Date.valueOf(sc.nextLine().trim());

            System.out.print("Enter venue: ");
            String venue = sc.nextLine().trim();

            double price = readDouble("Enter ticket price: ₹");
            int tickets = readInt("Enter available tickets: ");

            if (name.isEmpty() || venue.isEmpty()
                    || price < 0 || tickets < 0) {
                System.out.println("Please enter valid event details.");
                return;
            }

            boolean added =
                    eventService.addEvent(name, date, venue, price, tickets);

            if (added) {
                System.out.println("Event added successfully!");
            } else {
                System.out.println("Event could not be added.");
            }

        } catch (IllegalArgumentException e) {
            System.out.println("Invalid date. Use YYYY-MM-DD, e.g. 2026-12-15.");
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    static void customerMenu(User user) {
        int choice;

        do {
            System.out.println("\n========== CUSTOMER MENU ==========");
            System.out.println("1. View Events");
            System.out.println("2. Book Tickets");
            System.out.println("3. View My Bookings");
            System.out.println("4. Logout");

            choice = readInt("Enter your choice: ");

            switch (choice) {
                case 1:
                    viewEvents();
                    break;

                case 2:
                    bookTickets(user);
                    break;

                case 3:
                    try {
                        bookingService.viewMyBookings(user.getUserId());
                    } catch (SQLException e) {
                        System.out.println("Could not retrieve bookings: "
                                + e.getMessage());
                    }
                    break;

                case 4:
                    System.out.println("Customer logged out.");
                    break;

                default:
                    System.out.println("Invalid choice.");
            }

        } while (choice != 4);
    }

    static void viewEvents() {
        try {
            eventService.viewEvents();
        } catch (SQLException e) {
            System.out.println("Could not retrieve events: " + e.getMessage());
        }
    }

    static void bookTickets(User user) {
        System.out.println("\n========== BOOK TICKETS ==========");

        int eventId = readInt("Enter event ID: ");
        int quantity = readInt("Enter number of tickets: ");

        try {
            bookingService.bookTickets(
                    user.getUserId(), eventId, quantity);
        } catch (SQLException e) {
            System.out.println("Booking failed: " + e.getMessage());
        }
    }

    static int readInt(String message) {
        while (true) {
            System.out.print(message);

            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    static double readDouble(String message) {
        while (true) {
            System.out.print(message);

            try {
                return Double.parseDouble(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
}
