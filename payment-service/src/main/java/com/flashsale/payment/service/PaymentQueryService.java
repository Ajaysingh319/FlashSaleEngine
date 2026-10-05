package com.flashsale.payment.service;

import com.flashsale.payment.document.Payment;
import com.flashsale.payment.dto.PaymentResponse;
import com.flashsale.payment.exception.PaymentAccessDeniedException;
import com.flashsale.payment.exception.PaymentNotFoundException;
import com.flashsale.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Read access to payments: the paying customer sees their own payments; ADMIN monitors all (PRD 6.14). */
@Service
@RequiredArgsConstructor
public class PaymentQueryService {
    private final PaymentRepository paymentRepository;

    public PaymentResponse getPayment(String paymentId, String callerId, boolean callerIsAdmin) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (!callerIsAdmin && !payment.isOwnedBy(callerId)) {
            throw new PaymentAccessDeniedException();
        }
        return PaymentResponse.from(payment);
    }
}
