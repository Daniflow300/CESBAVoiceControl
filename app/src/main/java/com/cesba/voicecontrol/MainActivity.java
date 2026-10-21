
package com.cesba.voicecontrol;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PERMISO_MICROFONO = 101;

    private final Locale idioma = Locale.forLanguageTag("es-MX");

    private SpeechRecognizer reconocedor;
    private TextToSpeech voz;

    private boolean escuchando = false;
    private boolean vozLista = false;
    private boolean destruida = false;

    private TextView txtEstado;
    private TextView txtReconocido;
    private TextView txtRespuesta;

    private Button btnEscuchar;
    private Button btnCancelar;

    private LinearLayout mainPanel;
    private LinearLayout panelCalculadora;

    private EditText numA;
    private EditText numB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtEstado = findViewById(R.id.txtEstado);
        txtReconocido = findViewById(R.id.txtReconocido);
        txtRespuesta = findViewById(R.id.txtRespuesta);

        btnEscuchar = findViewById(R.id.btnEscuchar);
        btnCancelar = findViewById(R.id.btnCancelar);

        mainPanel = findViewById(R.id.mainPanel);
        panelCalculadora = findViewById(R.id.panelCalculadora);

        numA = findViewById(R.id.numA);
        numB = findViewById(R.id.numB);

        btnEscuchar.setOnClickListener(v -> solicitarOEscuchar());
        btnCancelar.setOnClickListener(v -> cancelarEscucha());

        findViewById(R.id.btnSumar).setOnClickListener(v -> sumar());

        findViewById(R.id.btnCerrarCalc).setOnClickListener(v -> {
            panelCalculadora.setVisibility(View.GONE);
            responder("Calculadora cerrada");
        });

        btnCancelar.setEnabled(false);

        prepararVoz();
        prepararReconocimiento();
    }

    // 1. Preparar la voz sintetica
    private void prepararVoz() {
        voz = new TextToSpeech(this, estado -> {
            if (destruida || voz == null) return;

            if (estado == TextToSpeech.SUCCESS) {
                int resultado = voz.setLanguage(idioma);

                vozLista = resultado != TextToSpeech.LANG_MISSING_DATA
                        && resultado != TextToSpeech.LANG_NOT_SUPPORTED;

                if (!vozLista) {
                    txtEstado.setText("Instala los datos de voz en español");
                }
            } else {
                vozLista = false;
            }
        });
    }

    // 2. Configurar reconocimiento de voz
    private void prepararReconocimiento() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            txtEstado.setText("Reconocimiento no disponible");
            btnEscuchar.setEnabled(false);
            return;
        }

        reconocedor = SpeechRecognizer.createSpeechRecognizer(this);

        reconocedor.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                txtEstado.setText("Escuchando...");
            }

            @Override
            public void onBeginningOfSpeech() {
                txtEstado.setText("Voz detectada");
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                txtEstado.setText("Procesando...");
            }

            @Override
            public void onError(int error) {
                finalizarEscucha();

                if (destruida) return;

                String mensaje;

                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        mensaje = "No reconocí las palabras";
                        break;

                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        mensaje = "No detecté tu voz";
                        break;

                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        mensaje = "Error de conexión";
                        break;

                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        mensaje = "Sin permiso del micrófono";
                        break;

                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        mensaje = "Reconocedor ocupado";
                        break;

                    default:
                        mensaje = "Error de reconocimiento: " + error;
                }

                txtEstado.setText("Error");
                txtRespuesta.setText(mensaje);
            }

            @Override
            public void onResults(Bundle results) {
                finalizarEscucha();

                if (destruida) return;

                ArrayList<String> frases = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);

                if (frases == null || frases.isEmpty()) {
                    txtRespuesta.setText("No se obtuvo una transcripción");
                    return;
                }

                String frase = frases.get(0);
                txtReconocido.setText(frase);
                procesarComando(frase);
            }

            @Override
            public void onPartialResults(Bundle results) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        txtEstado.setText("Listo para escuchar");
    }

    // 3. Solicitar permiso del microfono
    private void solicitarOEscuchar() {
        if (destruida || escuchando || reconocedor == null) return;

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISO_MICROFONO
            );
            return;
        }

        iniciarEscucha();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == PERMISO_MICROFONO) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarEscucha();
            } else {
                responder("Debes autorizar el micrófono");
            }
        }
    }

    // 4. Iniciar reconocimiento
    private void iniciarEscucha() {
        if (destruida || escuchando || reconocedor == null) return;

        if (voz != null) {
            voz.stop();
        }

        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        escuchando = true;
        btnEscuchar.setEnabled(false);
        btnCancelar.setEnabled(true);

        txtEstado.setText("Preparando micrófono...");

        try {
            reconocedor.startListening(intent);
        } catch (Exception e) {
            finalizarEscucha();
            txtEstado.setText("No se pudo iniciar el reconocimiento");
            txtRespuesta.setText(String.valueOf(e.getMessage()));
        }
    }

    // 5. Cancelar escucha
    private void cancelarEscucha() {
        if (!escuchando || reconocedor == null) return;

        reconocedor.cancel();
        finalizarEscucha();
        txtEstado.setText("Escucha cancelada");
    }

    private void finalizarEscucha() {
        escuchando = false;

        if (btnEscuchar != null) {
            btnEscuchar.setEnabled(reconocedor != null && !destruida);
        }

        if (btnCancelar != null) {
            btnCancelar.setEnabled(false);
        }
    }

    // 6. Normalizar texto
    private String normalizar(String texto) {
        String resultado = Normalizer.normalize(
                texto.toLowerCase(idioma),
                Normalizer.Form.NFD);

        resultado = resultado.replaceAll("\\p{M}+", "");
        resultado = resultado.replaceAll("[^a-z0-9 ]", " ");

        return resultado.replaceAll("\\s+", " ").trim();
    }

    // 7. Interpretar comandos
    private void procesarComando(String frase) {
        String comando = normalizar(frase);

        // Evitar ejecutar ordenes negadas
        if (comando.matches(".*\\b(no|nunca|evita|sin)\\b.*")) {
            responder("Detecté una negación. No ejecutaré la acción.");
            return;
        }

        if (comando.equals("hola cesba") || comando.equals("hola")) {
            responder("Hola. Bienvenido a CESBA Voice Control.");

        } else if (comando.contains("que hora es")
                || comando.equals("hora")) {

            String hora = new SimpleDateFormat("HH:mm", idioma)
                    .format(new Date());

            responder("Son las " + hora);

        } else if (comando.contains("que fecha es")
                || comando.contains("fecha de hoy")) {

            String fecha = new SimpleDateFormat(
                    "EEEE d 'de' MMMM 'de' yyyy", idioma)
                    .format(new Date());

            responder("Hoy es " + fecha);

        } else if (comando.equals("cambiar a azul")) {
            mainPanel.setBackgroundColor(Color.rgb(24, 42, 87));
            responder("Color azul aplicado");

        } else if (comando.equals("cambiar a rojo")) {
            mainPanel.setBackgroundColor(Color.rgb(126, 34, 52));
            responder("Color rojo aplicado");

        } else if (comando.equals("abrir calculadora")) {
            panelCalculadora.setVisibility(View.VISIBLE);
            responder("Calculadora abierta");

        } else if (comando.equals("leer mensaje")) {
            responder("La programación móvil permite crear aplicaciones Android.");

        } else if (comando.equals("ayuda")) {
            responder("Puedes decir: hola CESBA, qué hora es, "
                    + "qué fecha es hoy, cambiar a azul, cambiar a rojo, "
                    + "abrir calculadora, leer mensaje o ayuda.");

        } else {
            responder("Comando desconocido. Di ayuda para conocer las instrucciones.");
        }
    }

    // 8. Calculadora: sumar dos numeros
    private void sumar() {
        try {
            String textoA = numA.getText().toString().trim();
            String textoB = numB.getText().toString().trim();

            if (textoA.isEmpty() || textoB.isEmpty()) {
                responder("Introduce los dos números");
                return;
            }

            double a = Double.parseDouble(textoA);
            double b = Double.parseDouble(textoB);
            double resultado = a + b;

            responder("El resultado es " + resultado);

        } catch (NumberFormatException e) {
            responder("Introduce dos números válidos");
        }
    }

    // 9. Respuestas habladas
    private void responder(String mensaje) {
        txtRespuesta.setText(mensaje);
        txtEstado.setText("Listo para escuchar");

        if (vozLista && voz != null) {
            voz.speak(
                    mensaje,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "cesba-" + System.currentTimeMillis()
            );
        }
    }

    // 10. Liberar recursos
    @Override
    protected void onDestroy() {
        destruida = true;

        if (reconocedor != null) {
            reconocedor.cancel();
            reconocedor.destroy();
            reconocedor = null;
        }

        if (voz != null) {
            voz.stop();
            voz.shutdown();
            voz = null;
        }

        super.onDestroy();
    }
}
