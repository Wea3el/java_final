package com.expensesplitter.algorithm;

import com.expensesplitter.model.Settlement;
import java.util.*;

/**
 * Greedy minimum-transfer debt settlement: reduces O(n^2) pairwise debts to O(n-1) transfers.
 */
public class DebtSettler {

    public static List<Settlement> computeSettlements(Map<String, Double> balances) {
        List<Settlement> settlements = new ArrayList<>();

        // Max-heap for creditors (owed money), min-heap for debtors (owe money)
        PriorityQueue<Map.Entry<String, Double>> creditors =
            new PriorityQueue<>((a, b) -> Double.compare(b.getValue(), a.getValue()));
        PriorityQueue<Map.Entry<String, Double>> debtors =
            new PriorityQueue<>(Comparator.comparingDouble(Map.Entry::getValue));

        for (Map.Entry<String, Double> entry : balances.entrySet()) {
            double bal = Math.round(entry.getValue() * 100.0) / 100.0;
            if (bal > 0.005)
                creditors.offer(new AbstractMap.SimpleEntry<>(entry.getKey(), bal));
            else if (bal < -0.005)
                debtors.offer(new AbstractMap.SimpleEntry<>(entry.getKey(), bal));
        }

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Map.Entry<String, Double> creditor = creditors.poll();
            Map.Entry<String, Double> debtor = debtors.poll();

            double payment = Math.min(creditor.getValue(), -debtor.getValue());
            payment = Math.round(payment * 100.0) / 100.0;

            settlements.add(new Settlement(debtor.getKey(), creditor.getKey(), payment));

            double creditorRemaining = Math.round((creditor.getValue() - payment) * 100.0) / 100.0;
            double debtorRemaining  = Math.round((debtor.getValue()  + payment) * 100.0) / 100.0;

            if (creditorRemaining > 0.005)
                creditors.offer(new AbstractMap.SimpleEntry<>(creditor.getKey(), creditorRemaining));
            if (debtorRemaining < -0.005)
                debtors.offer(new AbstractMap.SimpleEntry<>(debtor.getKey(), debtorRemaining));
        }

        return settlements;
    }
}
