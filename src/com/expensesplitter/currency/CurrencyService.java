package com.expensesplitter.currency;

import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class CurrencyService {
    private static final String API_URL = "https://open.er-api.com/v6/latest/";
    private final HttpClient httpClient;
    private final Map<String, Map<String, Double>> rateCache = new HashMap<>();

    // Fallback rates relative to USD when API is unavailable
    private static final Map<String, Double> FALLBACK_RATES = Map.of(
        "USD", 1.0, "EUR", 0.92, "GBP", 0.79,
        "JPY", 149.5, "CAD", 1.36, "AUD", 1.53,
        "CNY", 7.24, "INR", 83.1, "MXN", 17.2, "CHF", 0.89
    );

    public CurrencyService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public Map<String, Double> getRates(String baseCurrency) {
        if (rateCache.containsKey(baseCurrency)) return rateCache.get(baseCurrency);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + baseCurrency))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());

            if ("success".equals(json.optString("result"))) {
                JSONObject ratesObj = json.getJSONObject("rates");
                Map<String, Double> rateMap = new HashMap<>();
                for (String key : ratesObj.keySet())
                    rateMap.put(key, ratesObj.getDouble(key));
                rateCache.put(baseCurrency, rateMap);
                return rateMap;
            }
        } catch (Exception e) {
            System.err.println("Currency API unavailable, using fallback rates: " + e.getMessage());
        }

        // Build fallback: convert FALLBACK_RATES to requested base
        Map<String, Double> fallback = new HashMap<>();
        double baseRate = FALLBACK_RATES.getOrDefault(baseCurrency, 1.0);
        for (Map.Entry<String, Double> entry : FALLBACK_RATES.entrySet())
            fallback.put(entry.getKey(), entry.getValue() / baseRate);
        return fallback;
    }

    public double convert(double amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) return amount;
        Map<String, Double> rates = getRates(fromCurrency);
        Double rate = rates.get(toCurrency);
        return rate != null ? amount * rate : amount;
    }

    public static String[] getSupportedCurrencies() {
        return new String[]{"USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CNY", "INR", "MXN", "CHF"};
    }
}
