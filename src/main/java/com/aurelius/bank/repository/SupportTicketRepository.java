package com.aurelius.bank.repository;

import com.aurelius.bank.model.SupportTicket;
import com.aurelius.bank.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByCustomerOrderByCreatedAtDesc(User customer);

    List<SupportTicket> findByAssignedToOrderByCreatedAtDesc(User staff);

    List<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus status);

    List<SupportTicket> findAllByOrderByCreatedAtDesc();
}
