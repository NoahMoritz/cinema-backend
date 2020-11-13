/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 11 2020
 */

package de.noamo.cinema.backend;

import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.orders.Order;
import com.paypal.orders.OrdersGetRequest;
import com.paypal.orders.PurchaseUnit;

import java.io.IOException;
import java.util.List;

public abstract class PayPal {
    private static PayPalHttpClient client;

    static void setupPayPal(String pClientId, String pClientSecret) {
        PayPalEnvironment environment = new PayPalEnvironment.Sandbox(pClientId, pClientSecret);
        client = new PayPalHttpClient(environment);
    }

    static boolean confirmPayment(String pOrderId, double pTargetAmount) throws IOException {
        try {
            OrdersGetRequest request = new OrdersGetRequest(pOrderId);
            HttpResponse<Order> response = client.execute(request);
            List<PurchaseUnit> purchaseUnits = response.result().purchaseUnits();
            if (purchaseUnits.size() == 0) return false;
            double value = Double.parseDouble(purchaseUnits.get(0).amountWithBreakdown().value());
            if (value == pTargetAmount) return true;
            Start.log(2, "Möglicher Betrug über PayPal bei OrderId " + pOrderId + " erkannt (Zielbetrag: " + pTargetAmount + ", gezahlter Betrag: " + value + ")");
        } catch (Exception ex) {
            Start.log(2, ex.getMessage());
        }
        return false;
    }
}
