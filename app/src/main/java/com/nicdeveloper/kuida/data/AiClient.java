package com.nicdeveloper.kuida.data;

import com.nicdeveloper.kuida.BuildConfig;

import android.graphics.Bitmap;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiClient {
    public interface Callback {
        void onResult(String text);
        void onError(String message);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public boolean hasAnyProvider() {
        return !BuildConfig.GEMINI_API_KEY.isEmpty()
                || !BuildConfig.OPENROUTER_API_KEY.isEmpty();
    }

    public String providerName() {
        if (!BuildConfig.GEMINI_API_KEY.isEmpty()) return "Gemini";
        if (!BuildConfig.OPENROUTER_API_KEY.isEmpty()) return "OpenRouter";
        return "Sin IA";
    }

    public void summarizeAccessible(String prompt, Callback callback) {
        executor.execute(() -> {
            try {
                if (!BuildConfig.GEMINI_API_KEY.isEmpty()) {
                    callback.onResult(callGemini(prompt));
                } else if (!BuildConfig.OPENROUTER_API_KEY.isEmpty()) {
                    callback.onResult(callOpenAiCompatible(BuildConfig.OPENROUTER_ENDPOINT, BuildConfig.OPENROUTER_API_KEY, BuildConfig.OPENROUTER_MODEL, prompt));
                } else {
                    callback.onError("No hay proveedor IA configurado. Agrega GEMINI_API_KEY u OPENROUTER_API_KEY en local.properties.");
                }
            } catch (Exception error) {
                callback.onError("No se pudo consultar IA: " + error.getMessage());
            }
        });
    }

    public void analyzeImage(String prompt, Bitmap bitmap, Callback callback) {
        executor.execute(() -> {
            try {
                String imageBase64 = encodeJpeg(bitmap);
                if (!BuildConfig.GEMINI_API_KEY.isEmpty()) {
                    callback.onResult(callGeminiVision(prompt, imageBase64));
                } else if (!BuildConfig.OPENROUTER_API_KEY.isEmpty()) {
                    callback.onResult(callOpenRouterVision(prompt, imageBase64));
                } else {
                    callback.onError("No hay IA visual configurada. Agrega GEMINI_API_KEY u OPENROUTER_API_KEY en local.properties.");
                }
            } catch (Exception error) {
                callback.onError("No se pudo analizar la imagen: " + error.getMessage());
            }
        });
    }

    private String callGemini(String prompt) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + BuildConfig.GEMINI_MODEL + ":generateContent?key=" + BuildConfig.GEMINI_API_KEY;
        JSONObject text = new JSONObject().put("text", prompt);
        JSONObject part = new JSONObject().put("parts", new JSONArray().put(text));
        JSONObject body = new JSONObject().put("contents", new JSONArray().put(part));
        JSONObject response = postJson(endpoint, null, body, 8000, 15000);
        return response.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .optString("text", "Sin respuesta de IA.");
    }

    private String callGeminiVision(String prompt, String imageBase64) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + BuildConfig.GEMINI_MODEL + ":generateContent?key=" + BuildConfig.GEMINI_API_KEY;
        JSONObject text = new JSONObject().put("text", prompt);
        JSONObject image = new JSONObject()
                .put("inline_data", new JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", imageBase64));
        JSONObject content = new JSONObject().put("parts", new JSONArray().put(text).put(image));
        JSONObject body = new JSONObject().put("contents", new JSONArray().put(content));
        JSONObject response = postJson(endpoint, null, body, 10000, 45000);
        return response.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .optString("text", "Sin respuesta visual de IA.");
    }

    private String callOpenAiCompatible(String endpoint, String apiKey, String model, String prompt) throws Exception {
        JSONObject message = new JSONObject()
                .put("role", "user")
                .put("content", prompt);
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", new JSONArray().put(message));
        JSONObject response = postJson(endpoint + "/chat/completions", apiKey, body, 8000, 15000);
        return response.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content", "Sin respuesta de IA.");
    }

    private String callOpenRouterVision(String prompt, String imageBase64) throws Exception {
        JSONObject text = new JSONObject()
                .put("type", "text")
                .put("text", prompt);
        JSONObject imageUrl = new JSONObject()
                .put("url", "data:image/jpeg;base64," + imageBase64);
        JSONObject image = new JSONObject()
                .put("type", "image_url")
                .put("image_url", imageUrl);
        JSONObject message = new JSONObject()
                .put("role", "user")
                .put("content", new JSONArray().put(text).put(image));
        JSONObject body = new JSONObject()
                .put("model", BuildConfig.OPENROUTER_MODEL_MULTIMODAL)
                .put("messages", new JSONArray().put(message));
        JSONObject response = postJson(BuildConfig.OPENROUTER_ENDPOINT + "/chat/completions", BuildConfig.OPENROUTER_API_KEY, body, 10000, 45000);
        return response.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content", "Sin respuesta visual de IA.");
    }

    /**
     * Limpia el formato markdown de la respuesta de la IA para que el texto se vea
     * bien en pantalla y el lector de voz no diga "asterisco".
     * "**mesa**" -> "mesa", "# titulo" -> "titulo", "- item" -> "item".
     */
    public static String cleanMarkdown(String text) {
        if (text == null) return "";
        String s = text;
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "$1"); // negrita **x**
        s = s.replaceAll("__(.+?)__", "$1"); // subrayado __x__
        s = s.replace("*", ""); // asteriscos sueltos
        s = s.replace("`", ""); // codigo
        s = s.replaceAll("(?m)^\\s{0,3}#{1,6}\\s?", ""); // encabezados # x
        s = s.replaceAll("(?m)^\\s*[-•]\\s+", ""); // vinetas - x
        s = s.replaceAll("(?<!\\w)_+(?!\\w)", ""); // guiones bajos sueltos (respeta dentro de palabras)
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    private String encodeJpeg(Bitmap bitmap) {
        Bitmap processed = bitmap;
        if (bitmap.getWidth() > 720) {
            float ratio = 720.0f / (float) bitmap.getWidth();
            int newHeight = Math.round(bitmap.getHeight() * ratio);
            processed = Bitmap.createScaledBitmap(bitmap, 720, newHeight, true);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        processed.compress(Bitmap.CompressFormat.JPEG, 70, output);
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    private JSONObject postJson(String endpoint, String bearer, JSONObject body,
                                int connectTimeoutMs, int readTimeoutMs) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        if (bearer != null && !bearer.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + bearer);
        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                StandardCharsets.UTF_8));
        StringBuilder raw = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) raw.append(line);
        reader.close();
        if (connection.getResponseCode() >= 400) throw new IllegalStateException(raw.toString());
        return new JSONObject(raw.toString());
    }
}
