package afg.achat.afgApprovAchat.service.util;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class IdGenerator {
    private final DataSource dataSource;

    public String generateId(String productIdPrefix , String sequenceName) {
        String productId = "";
        try {
            Connection connection = this.dataSource.getConnection();
            String query = "SELECT nextval(?)";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, sequenceName);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                productId = productIdPrefix + String.format("%08d", resultSet.getInt(1));
            } else {
                throw new SQLException("Impossible de recuperer la prochaine valeur de la sequence");
            }
            resultSet.close();
            preparedStatement.close();
            connection.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return productId;
    }

    public int getNextNumeroBC() {
        int annee = LocalDate.now().getYear();

        try (Connection connection = dataSource.getConnection()) {

            connection.setAutoCommit(false);

            try {
                // Verrouille la ligne de l'année
                String select = """
                    SELECT dernier_numero
                    FROM compteur_bc
                    WHERE annee = ?
                    FOR UPDATE
                    """;

                PreparedStatement ps = connection.prepareStatement(select);
                ps.setInt(1, annee);

                ResultSet rs = ps.executeQuery();

                int prochainNumero;

                if (rs.next()) {
                    prochainNumero = rs.getInt("dernier_numero") + 1;

                    PreparedStatement update = connection.prepareStatement(
                            "UPDATE compteur_bc SET dernier_numero = ? WHERE annee = ?");
                    update.setInt(1, prochainNumero);
                    update.setInt(2, annee);
                    update.executeUpdate();
                    update.close();

                } else {
                    prochainNumero = 1;

                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO compteur_bc(annee, dernier_numero) VALUES (?, ?)");
                    insert.setInt(1, annee);
                    insert.setInt(2, prochainNumero);
                    insert.executeUpdate();
                    insert.close();
                }

                rs.close();
                ps.close();

                connection.commit();
                return prochainNumero;

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération du numéro BC.", e);
        }
    }

    public String generateNumeroBC() {
        int numero = getNextNumeroBC();
        return FormatNumber.formatDate() + "-" + numero;
    }
}
