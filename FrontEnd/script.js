
const API_URL = "http://localhost:8080/api";

// Load events from Java backend and MySQL
async function loadEvents() {
    const container = document.getElementById("events-container");

    try {
        const response = await fetch(`${API_URL}/events`);

        if (!response.ok) {
            throw new Error("Unable to load events.");
        }

        const events = await response.json();

        if (events.length === 0) {
            container.innerHTML = "<p>No events available right now.</p>";
            return;
        }

        container.innerHTML = events.map(event => `
            <div class="event-card">
                <div class="event-image comedy">🎟️</div>
                <div class="event-info">
                    <p class="event-category">LIVE EVENT</p>
                    <h3>${escapeHTML(event.name)}</h3>
                    <p>📍 ${escapeHTML(event.venue)}</p>
                    <p>📅 ${escapeHTML(event.date)}</p>
                    <p>🎫 ${event.availableTickets} tickets available</p>
                    <div class="event-bottom">
                        <strong>₹${Number(event.price).toFixed(0)}</strong>
                        <button
                            onclick="bookEvent(${event.id})"
                            ${event.availableTickets <= 0 ? "disabled" : ""}>
                            Book Tickets
                        </button>
                    </div>
                </div>
            </div>
        `).join("");

    } catch (error) {
        console.error(error);
        container.innerHTML =
            "<p>Unable to load events. Check that the Java API is running.</p>";
    }
}

// Escape database text before inserting it into HTML
function escapeHTML(value) {
    return String(value).replace(/[&<>"']/g, character => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[character]);
}

// Register a new customer
async function showRegister() {
    const username = prompt("Choose a username:");
    if (username === null) return;

    const password = prompt("Choose a password:");
    if (password === null) return;

    if (!username.trim() || !password.trim()) {
        alert("Username and password cannot be empty.");
        return;
    }

    try {
        const response = await fetch(`${API_URL}/register`, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: new URLSearchParams({
                username: username.trim(),
                password: password
            })
        });

        const result = await response.json();

        if (!response.ok || result.success !== true) {
            throw new Error(
                result.message || "Registration failed. Try another username."
            );
        }

        alert("Registration successful! Please log in.");
        await showLogin();

    } catch (error) {
        alert(error.message);
    }
}

// Log in an existing customer or admin
async function showLogin() {
    const username = prompt("Enter your username:");
    if (username === null) return;

    const password = prompt("Enter your password:");
    if (password === null) return;

    try {
        const response = await fetch(`${API_URL}/login`, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: new URLSearchParams({
                username: username.trim(),
                password: password
            })
        });

        const result = await response.json();

        if (!response.ok || result.success !== true) {
            throw new Error(result.message || "Login failed.");
        }

        sessionStorage.setItem("eventlyUserId", result.userId);
        sessionStorage.setItem("eventlyUsername", result.username);
        sessionStorage.setItem("eventlyRole", result.role);
				updateNavbar();

        alert(`Welcome, ${result.username}! You are logged in.`);
    } catch (error) {
        alert(error.message);
    }
}

// Book tickets using the logged-in user's ID
async function bookEvent(eventId) {
    const userId = sessionStorage.getItem("eventlyUserId");

    if (!userId) {
        alert("Please log in before booking tickets.");
        await showLogin();

        // Stop here if login did not succeed
        if (!sessionStorage.getItem("eventlyUserId")) return;
    }

    const quantityInput = prompt("How many tickets would you like to book?");
    if (quantityInput === null) return;

    const quantity = Number(quantityInput);

    if (!Number.isInteger(quantity) || quantity <= 0) {
        alert("Enter a valid positive number of tickets.");
        return;
    }

    try {
        const response = await fetch(`${API_URL}/bookings`, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: new URLSearchParams({
                userId: sessionStorage.getItem("eventlyUserId"),
                eventId: eventId,
                quantity: quantity
            })
        });

        const result = await response.json();

        if (!response.ok || result.success !== true) {
            throw new Error(result.message || "Booking failed.");
        }

        alert("Tickets booked successfully!");
        await loadEvents();

    } catch (error) {
        alert(error.message);
    }
}

document.addEventListener("DOMContentLoaded", loadEvents);


function updateNavbar() {
    const username = sessionStorage.getItem("eventlyUsername");
    const role = sessionStorage.getItem("eventlyRole");
    const navButtons = document.querySelector(".nav-buttons");

    if (!navButtons) return;

    if (username) {
        navButtons.innerHTML = `
            ${role === "ADMIN" ? `
                <button id="create-event-btn"
                        class="signup-btn"
                        onclick="showCreateEvent()">
                    + Create Event
                </button>
            ` : ""}
            <span class="logged-in-user">Hi, ${escapeHTML(username)}</span>
            <button class="signup-btn" onclick="logout()">Logout</button>
        `;
    } else {
        navButtons.innerHTML = `
            <button onclick="showLogin()">Login</button>
            <button class="signup-btn" onclick="showRegister()">Sign Up</button>
        `;
    }
}


function logout() {
    sessionStorage.removeItem("eventlyUserId");
    sessionStorage.removeItem("eventlyUsername");
    sessionStorage.removeItem("eventlyRole");

    updateNavbar();
    alert("You have logged out.");
}

document.addEventListener("DOMContentLoaded", updateNavbar);

function showCreateEvent() {
    const section = document.getElementById("create-event-section");

    if (section) {
        section.style.display = "block";
        section.scrollIntoView({ behavior: "smooth" });
    }
}

function updateCreateEventButton() {
    const role = sessionStorage.getItem("eventlyRole");
    const button = document.getElementById("create-event-btn");

    if (button) {
        button.style.display = role === "ADMIN" ? "inline-block" : "none";
    }
}

document.addEventListener("DOMContentLoaded", () => {
    updateCreateEventButton();

    const form = document.getElementById("create-event-form");
    if (!form) return;

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        const message = document.getElementById("create-event-message");

        const eventData = {
            name: document.getElementById("event-name").value.trim(),
            date: document.getElementById("event-date").value,
            venue: document.getElementById("event-venue").value.trim(),
            price: Number(document.getElementById("event-price").value),
            availableTickets: Number(
                document.getElementById("event-tickets").value
            )
        };

        if (!eventData.name || !eventData.date || !eventData.venue ||
            eventData.price <= 0 || eventData.availableTickets < 1) {
            message.textContent = "Please enter valid event details.";
            return;
        }

        message.textContent = "Publishing event...";

        try {
            const response = await fetch(`${API_URL}/events`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(eventData)
            });

            const result = await response.json();

            if (!response.ok) {
                throw new Error(result.message || "Could not create event.");
            }

            message.textContent = "Event published successfully!";

            form.reset();

            if (typeof loadEvents === "function") {
                await loadEvents();
            }
        } catch (error) {
            message.textContent =
                "Could not publish event: " + error.message;
        }
    });
});

/* =========================================
   EVENTLY: CUSTOMER BOOKINGS & ADMIN DASHBOARD
   ========================================= */

function setupBookingDashboard() {
    // Create the dashboard section if it does not exist.
    if (!document.getElementById("booking-dashboard")) {
        const section = document.createElement("section");

        section.id = "booking-dashboard";
        section.className = "events-section";
        section.style.display = "none";

        section.innerHTML = `
            <div class="section-heading">
                <p class="tagline">EVENTLY DASHBOARD</p>
                <h2 id="booking-dashboard-title">My Bookings</h2>
                <p id="booking-dashboard-subtitle">
                    View your ticket bookings.
                </p>
            </div>

            <div id="booking-summary"
                 style="margin:20px 0;"></div>

            <div id="booking-results"
                 style="overflow-x:auto;">
                <p>Loading bookings...</p>
            </div>

            <button type="button"
                    class="signup-btn"
                    onclick="hideBookingDashboard()">
                Back to Events
            </button>
        `;

        // Place the dashboard before the footer.
        const footer = document.querySelector("footer");

        if (footer) {
            footer.parentNode.insertBefore(section, footer);
        } else {
            document.body.appendChild(section);
        }
    }

    updateBookingNavigation();
}

// dashboard button for the logged-in user.
function updateBookingNavigation() {
    const nav = document.querySelector(".nav-buttons");
    if (!nav) return;

    const role = sessionStorage.getItem("eventlyRole");
    const userId = sessionStorage.getItem("eventlyUserId");

    let button = document.getElementById("booking-nav-button");

    if (!userId || !role) {
        if (button) button.remove();
        return;
    }

    if (!button) {
        button = document.createElement("button");
        button.id = "booking-nav-button";
        button.className = "signup-btn";
        nav.appendChild(button);
    }

    if (role === "ADMIN") {
        button.textContent = "Booking Dashboard";
    } else {
        button.textContent = "My Bookings";
    }

    button.onclick = showBookings;
}

// Open the dashboard.
async function showBookings() {
    const userId = sessionStorage.getItem("eventlyUserId");
    const role = sessionStorage.getItem("eventlyRole");

    if (!userId || !role) {
        alert("Please log in first.");
        return;
    }

    setupBookingDashboard();

    const section = document.getElementById("booking-dashboard");
    section.style.display = "block";
    section.scrollIntoView({ behavior: "smooth" });

    const isAdmin = role === "ADMIN";

    document.getElementById("booking-dashboard-title").textContent =
        isAdmin ? "Admin Booking Dashboard" : "My Bookings";

    document.getElementById("booking-dashboard-subtitle").textContent =
        isAdmin
            ? "View all customer bookings and ticket sales."
            : "View your booked tickets and booking details.";

    const results = document.getElementById("booking-results");
    const summary = document.getElementById("booking-summary");

    results.innerHTML = "<p>Loading bookings...</p>";
    summary.innerHTML = "";

    try {
        // Admins see all bookings; customers see only their own.
        const bookingUrl = isAdmin
            ? `${API_URL}/admin/bookings?userId=${encodeURIComponent(userId)}`
            : `${API_URL}/bookings?userId=${encodeURIComponent(userId)}`;

        const response = await fetch(bookingUrl);
        const bookings = await response.json();

        if (!response.ok) {
            throw new Error(bookings.message || "Could not load bookings.");
        }

        if (isAdmin) {
            await loadAdminSummary(userId, summary);
        }

        if (!Array.isArray(bookings) || bookings.length === 0) {
            results.innerHTML = "<p>No bookings found yet.</p>";
            return;
        }

        const rows = bookings.map(booking => `
            <tr>
                <td>${booking.bookingId}</td>
                ${isAdmin
                    ? `<td>${escapeBookingText(booking.username)}</td>`
                    : ""}
                <td>${escapeBookingText(booking.eventName)}</td>
                <td>${escapeBookingText(booking.eventDate)}</td>
                <td>${escapeBookingText(booking.venue)}</td>
                <td>${booking.ticketsBooked}</td>
                <td>₹${Number(booking.pricePerTicket).toFixed(2)}</td>
                <td>₹${Number(booking.totalAmount).toFixed(2)}</td>
                <td>${escapeBookingText(booking.bookingDate)}</td>
            </tr>
        `).join("");

        results.innerHTML = `
            <table style="
                width:100%;
                border-collapse:collapse;
                min-width:850px;">
                <thead>
                    <tr>
                        <th>Booking ID</th>
                        ${isAdmin ? "<th>Customer</th>" : ""}
                        <th>Event</th>
                        <th>Event Date</th>
                        <th>Venue</th>
                        <th>Tickets</th>
                        <th>Price/Ticket</th>
                        <th>Total</th>
                        <th>Booking Date</th>
                    </tr>
                </thead>
                <tbody>${rows}</tbody>
            </table>
        `;

        results.querySelectorAll("th, td").forEach(cell => {
            cell.style.padding = "12px";
            cell.style.borderBottom = "1px solid #ddd";
            cell.style.textAlign = "left";
        });

    } catch (error) {
        results.innerHTML = `
            <p>Unable to load bookings: ${escapeBookingText(error.message)}</p>
        `;
    }
}

// Show admin ticket sales and remaining ticket counts.
async function loadAdminSummary(userId, container) {
    try {
        const response = await fetch(
            `${API_URL}/admin/summary?userId=${encodeURIComponent(userId)}`
        );

        const events = await response.json();

        if (!response.ok) {
            throw new Error(events.message || "Could not load ticket summary.");
        }

        if (!events.length) {
            container.innerHTML = "<p>No events available.</p>";
            return;
        }

        container.innerHTML = `
            <h3>Ticket Sales by Event</h3>
            <div style="overflow-x:auto;">
                <table style="width:100%;border-collapse:collapse;">
                    <thead>
                        <tr>
                            <th>Event</th>
                            <th>Tickets Sold</th>
                            <th>Tickets Remaining</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${events.map(event => `
                            <tr>
                                <td>${escapeBookingText(event.eventName)}</td>
                                <td>${event.ticketsSold}</td>
                                <td>${event.ticketsRemaining}</td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>
            </div>
        `;

        container.querySelectorAll("th, td").forEach(cell => {
            cell.style.padding = "12px";
            cell.style.borderBottom = "1px solid #ddd";
            cell.style.textAlign = "left";
        });

    } catch (error) {
        container.innerHTML =
            `<p>Could not load ticket summary: ${escapeBookingText(error.message)}</p>`;
    }
}

// Escape database values before inserting them into HTML.
function escapeBookingText(value) {
    const element = document.createElement("span");
    element.textContent = value == null ? "" : String(value);
    return element.innerHTML;
}

function hideBookingDashboard() {
    const section = document.getElementById("booking-dashboard");

    if (section) {
        section.style.display = "none";
    }

    const events = document.getElementById("events");

    if (events) {
        events.scrollIntoView({ behavior: "smooth" });
    } else {
        window.scrollTo({ top: 0, behavior: "smooth" });
    }
}

// Keep the dashboard button updated after login and logout.
const originalUpdateNavbar = window.updateNavbar;

if (typeof originalUpdateNavbar === "function") {
    window.updateNavbar = function () {
        originalUpdateNavbar();
        updateBookingNavigation();
    };
}

document.addEventListener("DOMContentLoaded", () => {
    setupBookingDashboard();
});
