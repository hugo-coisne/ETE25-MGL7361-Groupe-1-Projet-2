package ca.uqam.mgl7361.lel.gp1.order.persistence;


import ca.uqam.mgl7361.lel.gp1.lel.gp1.account.dto.AccountDTO;
import ca.uqam.mgl7361.lel.gp1.lel.gp1.common.DBConnection;
import ca.uqam.mgl7361.lel.gp1.order.dto.OrderDTO;
import ca.uqam.mgl7361.lel.gp1.order.model.Order;
import ca.uqam.mgl7361.lel.gp1.shop.dto.BookDTO;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;


public class OrderDAO {
    private final Logger logger = Logger.getLogger(OrderDAO.class.getName());

    private String generateOrderNumber() {
        LocalDate today = LocalDate.now();
        String datePart = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        String uuidPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return datePart + "-" + uuidPart;
    }

    public Order createOrder(AccountDTO accountDTO, Map<BookDTO, Integer> books, double total_price) throws Exception {
        logger.log(Level.INFO, String.format("Creating order for account %s", accountDTO));

        String orderNumber = generateOrderNumber();
        Date date = new Date(System.currentTimeMillis());
        Order order;

        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement insertOrder = conn.prepareStatement(
                        "INSERT INTO orders (order_number, account_id, total_price, order_date) VALUES (?, ?, ?, ?)",
                        PreparedStatement.RETURN_GENERATED_KEYS
                );
                PreparedStatement insertContent = conn.prepareStatement(
                        "INSERT INTO order_contents (" +
                                "order_number, book_isbn, book_title, book_description, " +
                                "book_price, book_publisher, book_publication_date, " +
                                "book_authors, quantity) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )
        ) {
            conn.setAutoCommit(false);

            // Insert order
            insertOrder.setString(1, orderNumber);
            insertOrder.setInt(2, accountDTO.getId());
            insertOrder.setDouble(3, total_price);
            insertOrder.setDate(4, date);
            insertOrder.executeUpdate();

            // Insert contents
            for (Map.Entry<BookDTO, Integer> entry : books.entrySet()) {
                BookDTO book = entry.getKey();
                int quantity = entry.getValue();

                insertContent.setString(1, orderNumber);

                insertContent.setString(2, nullable(book.getIsbn()));
                insertContent.setString(3, nullable(book.getTitle()));
                insertContent.setString(4, nullable(book.getDescription()));

                if (book.getPrice() != 0.0) {
                    insertContent.setDouble(5, book.getPrice());
                } else {
                    insertContent.setNull(5, java.sql.Types.DECIMAL);
                }

                if (book.getPublisher() != null) {
                    insertContent.setString(6, book.getPublisher().getName());
                } else {
                    insertContent.setNull(6, java.sql.Types.VARCHAR);
                }

                if (book.getPublicationDate() != null) {
                    insertContent.setDate(7, book.getPublicationDate());
                } else {
                    insertContent.setNull(7, java.sql.Types.DATE);
                }

                if (book.getAuthors() != null && !book.getAuthors().isEmpty()) {
                    insertContent.setString(8, String.join(", ", book.getAuthors().stream()
                            .map(a -> a != null ? a.getName() : "")
                            .toList()));
                } else {
                    insertContent.setNull(8, java.sql.Types.VARCHAR);
                }

                insertContent.setInt(9, quantity);

                insertContent.addBatch(); // Add to batch for order contents
            }

            insertContent.executeBatch(); // Execute batch insert for order contents
            conn.commit(); // Commit the transaction

            order = new Order(orderNumber, date, books);
            order.setOrderPrice((float) total_price);
            logger.info("Order and order contents inserted successfully.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating order", e);
            throw new Exception("Error creating order: " + e.getMessage(), e);
        }

        return order;
    }

    private String nullable(String value) {
        return value != null ? value : null;
    }

    public int findIdByOrderNumber(String orderNumber) throws Exception {
        String sql = "SELECT id FROM orders WHERE order_number = ?";
        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, orderNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                } else {
                    throw new Exception("Order not found for order_number: " + orderNumber);
                }
            }
        } catch (Exception e) {
            throw new Exception("Error finding order id by order number: " + e.getMessage(), e);
        }
    }


    public OrderDTO findById(int orderId) throws Exception {
        String sql = "SELECT id, order_number, account_id, order_date, total_price FROM orders WHERE id = ?";

        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setInt(1, orderId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String orderNumber = rs.getString("order_number");
                    java.sql.Date orderDate = rs.getDate("order_date");
                    float orderPrice = (float) rs.getDouble("total_price");
                    Map<BookDTO, Integer> items = Map.of(); // Assuming items are fetched separately TODO
                    return new OrderDTO(orderNumber, orderDate, orderPrice, items);
                } else {
                    throw new Exception("No order found with id: " + orderId);
                }
            }
        } catch (Exception e) {
            throw new Exception("Error fetching order by ID: " + e.getMessage(), e);
        }
    }

}
