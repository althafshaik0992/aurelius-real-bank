package com.aurelius.bank.repository;

import com.aurelius.bank.model.ClientApplication;
import com.aurelius.bank.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientApplicationRepository extends JpaRepository<ClientApplication, Long> {
    List<ClientApplication> findByStatusOrderByCreatedAtDesc(ClientApplication.ApplicationStatus status);
    List<ClientApplication> findAllByOrderByCreatedAtDesc();
    Optional<ClientApplication> findByEmail(String email);
    Optional<ClientApplication> findByClientUser(User user);
    List<ClientApplication> findByCreatedByStaffOrderByCreatedAtDesc(User staff);

    // Find incomplete/broken applications
    @org.springframework.data.jpa.repository.Query(
        "SELECT a FROM ClientApplication a WHERE " +
        "a.fullName IS NULL OR a.fullName = '' OR " +
        "a.email IS NULL OR a.email = '' OR " +
        "a.status IS NULL"
    )
    List<ClientApplication> findIncomplete();

    // Native delete for broken records — bypasses any JPA/quoting issues
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
        value = "DELETE FROM client_applications WHERE full_name IS NULL OR full_name = '' OR email IS NULL OR email = ''",
        nativeQuery = true
    )
    int deleteAllIncompleteNative();
}
