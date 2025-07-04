package delivery.persistence;

import delivery.dto.DeliveryDTO;
import delivery.model.Delivery;
import delivery.persistence.DeliveryDAO;
import order.dto.OrderDTO;
import delivery.dto.AddressDTO;
import common.DBConnection;
import order.persistence.OrderDAO;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class DeliveryDAOTest {

    private static final Connection connection;

    static {
        try {
            connection = DBConnection.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    private static DeliveryDAO deliveryDAO;

    @BeforeAll
    public static void setupDatabase() throws Exception {
        deliveryDAO = new DeliveryDAO();

        Connection connection = DBConnection.getConnection(); // Connexion centralis�e
        try (Statement stmt = connection.createStatement()) {
            // 1. Charger le script init.sql
            String sqlInit = Files.readString(Paths.get("../../init.sql"));

            // 2. Diviser si plusieurs instructions
            for (String sql : sqlInit.split(";")) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    stmt.execute(sql);
                }
            }

            // Table accounts (pour les FK via orders ? accounts)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS accounts (
                    id INT PRIMARY KEY AUTO_INCREMENT
                );
            """);

            stmt.execute("INSERT INTO accounts (id) VALUES (1) ON DUPLICATE KEY UPDATE id = id");

            // Table orders
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    order_number VARCHAR(100) UNIQUE,
                    account_id INT,
                    order_date DATE,
                    total_price DECIMAL(10,2),
                    FOREIGN KEY (account_id) REFERENCES accounts(id)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS addresses (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    account_id INT,
                    first_name VARCHAR(50),
                    last_name VARCHAR(50),
                    phone VARCHAR(20),
                    street VARCHAR(100),
                    city VARCHAR(50),
                    postal_code VARCHAR(10),
                    FOREIGN KEY (account_id) REFERENCES accounts(id)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS deliveries (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    order_id INT UNIQUE,
                    address_id INT,
                    delivery_date DATE,
                    status VARCHAR(50),
                    FOREIGN KEY (order_id) REFERENCES orders(id),
                    FOREIGN KEY (address_id) REFERENCES addresses(id)
                );
            """);

            // Insert data required for test
            stmt.execute("INSERT INTO orders (order_number, account_id, order_date, total_price) " +
                    "VALUES ('ORDER-TESTDAO-001', 1, CURDATE(), 99.99) " +
                    "ON DUPLICATE KEY UPDATE total_price = 99.99");

            stmt.execute("INSERT INTO orders (order_number, account_id, order_date, total_price) " +
                    "VALUES ('ORDER-TESTDAO-002', 2, CURDATE(), 49.99) " +
                    "ON DUPLICATE KEY UPDATE total_price = 49.99");

            stmt.execute("INSERT INTO addresses (account_id, first_name, last_name, phone, street, city, postal_code) " +
                    "VALUES (1, 'Alice', 'Durand', '0601020304', '10 rue des Lilas', 'Paris', '75001') " +
                    "ON DUPLICATE KEY UPDATE city = 'Paris'");
        }
    }

    @AfterAll
    public static void closeDatabase() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    public void testCreateDelivery() throws Exception {
        // Arrange
        OrderDTO orderDTO = new OrderDTO(
                "ORDER-TESTDAO-001",
                LocalDate.now(),
                99.99f,
                null
        );

        AddressDTO addressDTO = new AddressDTO();
        addressDTO.setId(1);

        DeliveryDTO delivery = new DeliveryDTO();
        delivery.setOrder(orderDTO);
        delivery.setAddress(addressDTO);
        delivery.setDeliveryDate(LocalDate.now());
        delivery.setDeliveryStatus("In Transit");

        // Act
        deliveryDAO.createDelivery(delivery);

        // Assert
        assertNotNull(delivery);
        // Tu pourrais ajouter un findByOrderNumber ici pour valider l�insertion en base
    }

    @Test
    public void testUpdateDeliveryStatus() throws Exception {
        // Cr�er la livraison
        DeliveryDTO delivery = new DeliveryDTO();
        delivery.setOrder(new OrderDTO("ORDER-TESTDAO-002", LocalDate.now(), 49.99f, null));
        delivery.setAddress(new AddressDTO() {{ setId(1); }});
        delivery.setDeliveryDate(LocalDate.now());
        delivery.setDeliveryStatus("In Transit");

        // Ins�re si pas d�j� l�
        delivery = deliveryDAO.createDelivery(delivery);

        // Met � jour le statut
        delivery.setDeliveryStatus("Delivered");
        deliveryDAO.update(delivery);

        // Recharger pour v�rifier
        Delivery updated = deliveryDAO.findByOrderNumber(delivery.getOrder().getOrderNumber());
        assertEquals("Delivered", updated.getDeliveryStatus());
    }
}
