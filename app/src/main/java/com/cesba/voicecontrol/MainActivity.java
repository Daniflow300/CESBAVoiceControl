package com.cesba.voicecontrol;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int PERMISO_MICROFONO = 101;
    private static final int MAX_BURBUJAS = 40;

    // Colores de estado
    private static final int COLOR_DORADO = 0xFFF0B323;
    private static final int COLOR_ROJO = 0xFFFF6B6B;
    private static final int COLOR_VERDE = 0xFF3DD68C;
    private static final int COLOR_CELESTE = 0xFF7CC4FF;

    // Temas de fondo: {color superior, color inferior}
    private static final int[] TEMA_AZUL = {0xFF0B1533, 0xFF182A57};
    private static final int[] TEMA_ROJO = {0xFF2B0A12, 0xFF7E2234};
    private static final int[] TEMA_VERDE = {0xFF062A1F, 0xFF14684C};
    private static final int[] TEMA_MORADO = {0xFF1C0F3D, 0xFF4B2A93};
    private static final int[] TEMA_DORADO = {0xFF3A2A00, 0xFFB07F0A};
    private static final int[] TEMA_OSCURO = {0xFF000000, 0xFF1A1A1F};

    private static final Pattern CALCULO = Pattern.compile(
            "(-?\\d+(?:[.,]\\d+)?)\\s*"
                    + "(dividido entre|dividido por|dividido|multiplicado por|mas|menos|por|entre|x|\\+|-|\\*|/|×|÷)"
                    + "\\s*(-?\\d+(?:[.,]\\d+)?)");

    private static final Pattern TEMPORIZADOR = Pattern.compile(
            "(temporizador|cronometro|timer|alarma).*?(\\d+) (segundo|segundos|minuto|minutos)");

    private static final String[] CHISTES = {
            "¿Por qué los programadores prefieren el modo oscuro? Porque la luz atrae a los bugs.",
            "Un programador va al súper. Su pareja le dice: compra una barra de pan y, si hay huevos, trae seis. Volvió con seis barras de pan.",
            "Hay 10 tipos de personas: las que entienden binario y las que no.",
            "¿Cómo se despide un programador? Hasta la vista, byte.",
            "Mi código funciona y no sé por qué. Mi código no funciona y tampoco sé por qué."
    };

    private static final String[] SUGERENCIAS = {
            "Hola CESBA", "¿Qué hora es?", "¿Qué fecha es hoy?",
            "Abrir calculadora", "Cuánto es 12 por 8", "Cuéntame un chiste",
            "Cambiar a verde", "Temporizador 10 segundos", "Batería",
            "Tira un dado", "Cara o cruz", "Ayuda"
    };

    private final Locale idioma = Locale.forLanguageTag("es-MX");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random azar = new Random();
    private final ArgbEvaluator evaluador = new ArgbEvaluator();

    private SpeechRecognizer reconocedor;
    private TextToSpeech voz;

    private boolean escuchando = false;
    private boolean vozLista = false;
    private boolean destruida = false;

    private LinearLayout root;
    private LinearLayout chat;
    private LinearLayout panelCalculadora;
    private ScrollView scrollChat;
    private TextView txtEstado;
    private TextView txtParcial;
    private View viewRing;
    private View btnMic;
    private WaveView waveView;
    private EditText numA;
    private EditText numB;

    private GradientDrawable fondo;
    private int[] temaActual = TEMA_AZUL.clone();
    private ObjectAnimator pulso;
    private Runnable temporizador;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getActionBar() != null) getActionBar().hide();

        root = findViewById(R.id.root);
        chat = findViewById(R.id.chat);
        scrollChat = findViewById(R.id.scrollChat);
        panelCalculadora = findViewById(R.id.panelCalculadora);
        txtEstado = findViewById(R.id.txtEstado);
        txtParcial = findViewById(R.id.txtParcial);
        viewRing = findViewById(R.id.viewRing);
        btnMic = findViewById(R.id.btnMic);
        waveView = findViewById(R.id.waveView);
        numA = findViewById(R.id.numA);
        numB = findViewById(R.id.numB);

        // Fondo degradado que se puede animar entre temas
        fondo = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, temaActual.clone());
        root.setBackground(fondo);
        getWindow().setStatusBarColor(temaActual[0]);

        btnMic.setOnClickListener(v -> {
            if (escuchando) cancelarEscucha();
            else solicitarOEscuchar();
        });

        findViewById(R.id.btnLimpiar).setOnClickListener(v -> chat.removeAllViews());
        findViewById(R.id.btnSumar).setOnClickListener(v -> operarCalculadora("+"));
        findViewById(R.id.btnRestar).setOnClickListener(v -> operarCalculadora("-"));
        findViewById(R.id.btnMultiplicar).setOnClickListener(v -> operarCalculadora("*"));
        findViewById(R.id.btnDividir).setOnClickListener(v -> operarCalculadora("/"));
        findViewById(R.id.btnCerrarCalc).setOnClickListener(v -> {
            cerrarCalculadora();
            responder("Calculadora cerrada");
        });

        crearPulso();
        crearSugerencias();

        setEstado("Iniciando…", COLOR_DORADO);
        prepararVoz();
        prepararReconocimiento();

        agregarBurbuja("¡Hola! Toca el micrófono y dime un comando, "
                + "o elige uno de abajo. Prueba con \"ayuda\".", false);
    }

    // ---------------------------------------------------------------
    // Interfaz
    // ---------------------------------------------------------------

    private int dp(float valor) {
        return (int) (valor * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setEstado(String texto, int color) {
        txtEstado.setText(texto);
        txtEstado.setTextColor(color);
        GradientDrawable g = (GradientDrawable) txtEstado.getBackground().mutate();
        g.setStroke(dp(1), color);
    }

    private void crearPulso() {
        pulso = ObjectAnimator.ofPropertyValuesHolder(viewRing,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.4f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.4f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 0f));
        pulso.setDuration(1100);
        pulso.setRepeatCount(ValueAnimator.INFINITE);
        pulso.setInterpolator(new DecelerateInterpolator());
    }

    private void animarMic(boolean activo) {
        btnMic.setBackgroundResource(activo
                ? R.drawable.bg_mic_listening : R.drawable.bg_mic);
        waveView.setActive(activo);

        if (activo) {
            pulso.start();
        } else {
            pulso.cancel();
            viewRing.setScaleX(1f);
            viewRing.setScaleY(1f);
            viewRing.setAlpha(0.3f);
        }
    }

    private void crearSugerencias() {
        LinearLayout contenedor = findViewById(R.id.chips);

        for (String texto : SUGERENCIAS) {
            TextView chip = new TextView(this);
            chip.setText(texto);
            chip.setTextColor(0xFFFFFFFF);
            chip.setTextSize(13);
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setPadding(dp(14), dp(9), dp(14), dp(9));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                if (escuchando) cancelarEscucha();
                agregarBurbuja(texto, true);
                procesarComando(texto);
            });

            contenedor.addView(chip);
        }
    }

    private void agregarBurbuja(String texto, boolean usuario) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextSize(16);
        t.setTextColor(usuario ? 0xFF0B1533 : 0xFFFFFFFF);
        t.setBackgroundResource(usuario
                ? R.drawable.bg_bubble_user : R.drawable.bg_bubble_bot);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = usuario ? Gravity.END : Gravity.START;
        lp.topMargin = dp(8);
        lp.setMarginStart(usuario ? dp(48) : 0);
        lp.setMarginEnd(usuario ? 0 : dp(48));
        t.setLayoutParams(lp);

        t.setAlpha(0f);
        t.setTranslationY(dp(14));
        chat.addView(t);
        t.animate().alpha(1f).translationY(0f).setDuration(260).start();

        if (chat.getChildCount() > MAX_BURBUJAS) chat.removeViewAt(0);

        scrollChat.post(() -> scrollChat.fullScroll(View.FOCUS_DOWN));
    }

    private void aplicarTema(int[] destino) {
        final int[] origen = temaActual.clone();
        temaActual = destino.clone();

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(600);
        anim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            int c1 = (int) evaluador.evaluate(f, origen[0], destino[0]);
            int c2 = (int) evaluador.evaluate(f, origen[1], destino[1]);
            fondo.setColors(new int[]{c1, c2});
            getWindow().setStatusBarColor(c1);
        });
        anim.start();
    }

    private void abrirCalculadora() {
        panelCalculadora.setAlpha(0f);
        panelCalculadora.setVisibility(View.VISIBLE);
        panelCalculadora.animate().alpha(1f).setDuration(250).start();
    }

    private void cerrarCalculadora() {
        panelCalculadora.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> panelCalculadora.setVisibility(View.GONE))
                .start();
    }

    // ---------------------------------------------------------------
    // Voz sintetica (TextToSpeech)
    // ---------------------------------------------------------------

    private void prepararVoz() {
        voz = new TextToSpeech(this, estado -> {
            if (destruida || voz == null) return;

            if (estado == TextToSpeech.SUCCESS) {
                int resultado = voz.setLanguage(idioma);

                vozLista = resultado != TextToSpeech.LANG_MISSING_DATA
                        && resultado != TextToSpeech.LANG_NOT_SUPPORTED;

                if (!vozLista) {
                    setEstado("Instala los datos de voz en español", COLOR_ROJO);
                    return;
                }

                voz.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        runOnUiThread(() -> {
                            if (!destruida && !escuchando) {
                                setEstado("Hablando…", COLOR_CELESTE);
                            }
                        });
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        runOnUiThread(() -> {
                            if (!destruida && !escuchando) {
                                setEstado("Listo para escuchar", COLOR_VERDE);
                            }
                        });
                    }

                    @Override
                    public void onError(String utteranceId) {
                        onDone(utteranceId);
                    }
                });
            } else {
                vozLista = false;
            }
        });
    }

    // ---------------------------------------------------------------
    // Reconocimiento de voz
    // ---------------------------------------------------------------

    private void prepararReconocimiento() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setEstado("Reconocimiento no disponible", COLOR_ROJO);
            btnMic.setEnabled(false);
            btnMic.setAlpha(0.4f);
            return;
        }

        reconocedor = SpeechRecognizer.createSpeechRecognizer(this);

        reconocedor.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                setEstado("Escuchando…", COLOR_ROJO);
            }

            @Override
            public void onBeginningOfSpeech() {
                setEstado("Voz detectada", COLOR_ROJO);
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                waveView.setLevel(rmsdB);
            }

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                setEstado("Procesando…", COLOR_CELESTE);
                waveView.setActive(false);
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

                setEstado("Error", COLOR_ROJO);
                agregarBurbuja(mensaje, false);
            }

            @Override
            public void onResults(Bundle results) {
                finalizarEscucha();

                if (destruida) return;

                ArrayList<String> frases = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);

                if (frases == null || frases.isEmpty()) {
                    agregarBurbuja("No se obtuvo una transcripción", false);
                    return;
                }

                String frase = frases.get(0);
                agregarBurbuja(frase, true);
                procesarComando(frase);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> parcial = partialResults.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);

                if (parcial != null && !parcial.isEmpty()) {
                    txtParcial.setText("“" + parcial.get(0) + "”");
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        setEstado("Listo para escuchar", COLOR_VERDE);
    }

    private void solicitarOEscuchar() {
        if (destruida || escuchando || reconocedor == null) return;

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISO_MICROFONO);
            return;
        }

        iniciarEscucha();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISO_MICROFONO) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarEscucha();
            } else {
                responder("Debes autorizar el micrófono");
            }
        }
    }

    private void iniciarEscucha() {
        if (destruida || escuchando || reconocedor == null) return;

        if (voz != null) voz.stop();

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        escuchando = true;
        txtParcial.setText("");
        animarMic(true);
        setEstado("Preparando micrófono…", COLOR_DORADO);

        try {
            reconocedor.startListening(intent);
        } catch (Exception e) {
            finalizarEscucha();
            setEstado("No se pudo iniciar", COLOR_ROJO);
            agregarBurbuja(String.valueOf(e.getMessage()), false);
        }
    }

    private void cancelarEscucha() {
        if (!escuchando || reconocedor == null) return;

        reconocedor.cancel();
        finalizarEscucha();
        setEstado("Escucha cancelada", COLOR_DORADO);
    }

    private void finalizarEscucha() {
        escuchando = false;
        txtParcial.setText("");
        animarMic(false);
    }

    // ---------------------------------------------------------------
    // Interpretacion de comandos
    // ---------------------------------------------------------------

    /** Minusculas y sin acentos, conservando signos utiles para calculos. */
    private String limpiar(String texto) {
        String r = Normalizer.normalize(texto.toLowerCase(idioma), Normalizer.Form.NFD);
        return r.replaceAll("\\p{M}+", "").trim();
    }

    /** Solo letras, numeros y espacios. */
    private String normalizar(String texto) {
        String r = limpiar(texto).replaceAll("[^a-z0-9 ]", " ");
        return r.replaceAll("\\s+", " ").trim();
    }

    private void procesarComando(String frase) {
        String limpio = limpiar(frase);
        String comando = normalizar(frase);

        // Evitar ejecutar ordenes negadas
        if (comando.matches(".*\\b(no|nunca|evita|tampoco)\\b.*")) {
            responder("Detecté una negación, así que no ejecutaré la acción.");
            return;
        }

        // Operaciones matematicas por voz: "cuanto es 12 por 8"
        Matcher calculo = CALCULO.matcher(limpio);
        if (calculo.find()) {
            resolverOperacion(calculo);
            return;
        }

        // Temporizador
        if (comando.contains("cancelar temporizador")
                || comando.contains("detener temporizador")) {
            if (temporizador != null) {
                handler.removeCallbacks(temporizador);
                temporizador = null;
                responder("Temporizador cancelado");
            } else {
                responder("No hay ningún temporizador activo");
            }
            return;
        }

        Matcher tiempo = TEMPORIZADOR.matcher(comando);
        if (tiempo.find()) {
            iniciarTemporizador(Integer.parseInt(tiempo.group(2)), tiempo.group(3));
            return;
        }

        // Temas de color
        if (comando.matches(".*\\b(cambia|cambiar|pon|poner|modo|tema|color)\\b.*")) {
            if (comando.contains("azul")) {
                aplicarTema(TEMA_AZUL);
                responder("Tema azul aplicado");
                return;
            } else if (comando.contains("rojo")) {
                aplicarTema(TEMA_ROJO);
                responder("Tema rojo aplicado");
                return;
            } else if (comando.contains("verde")) {
                aplicarTema(TEMA_VERDE);
                responder("Tema verde aplicado");
                return;
            } else if (comando.contains("morado")) {
                aplicarTema(TEMA_MORADO);
                responder("Tema morado aplicado");
                return;
            } else if (comando.contains("dorado")) {
                aplicarTema(TEMA_DORADO);
                responder("Tema dorado aplicado");
                return;
            } else if (comando.contains("oscuro") || comando.contains("negro")) {
                aplicarTema(TEMA_OSCURO);
                responder("Modo oscuro activado");
                return;
            }
        }

        if (comando.startsWith("hola") || comando.contains("buenos dias")
                || comando.contains("buenas tardes") || comando.contains("buenas noches")) {
            responder("Hola. Bienvenido a CESBA Voice Control.");

        } else if (comando.contains("quien eres") || comando.contains("como te llamas")) {
            responder("Soy el asistente de voz de CESBA. Te escucho y ejecuto tus comandos.");

        } else if (comando.contains("gracias")) {
            responder("¡De nada! Para eso estoy.");

        } else if (comando.contains("que hora es") || comando.equals("hora")) {
            String hora = new SimpleDateFormat("HH:mm", idioma).format(new Date());
            responder("Son las " + hora);

        } else if (comando.contains("que fecha es") || comando.contains("fecha de hoy")
                || comando.contains("que dia es")) {
            String fecha = new SimpleDateFormat(
                    "EEEE d 'de' MMMM 'de' yyyy", idioma).format(new Date());
            responder("Hoy es " + fecha);

        } else if (comando.contains("abrir calculadora")) {
            abrirCalculadora();
            responder("Calculadora abierta");

        } else if (comando.contains("cerrar calculadora")) {
            cerrarCalculadora();
            responder("Calculadora cerrada");

        } else if (comando.contains("chiste")) {
            responder(CHISTES[azar.nextInt(CHISTES.length)]);

        } else if (comando.contains("dado")) {
            responder("Salió el " + (azar.nextInt(6) + 1));

        } else if (comando.contains("cara o cruz") || comando.contains("volado")) {
            responder(azar.nextBoolean() ? "Cayó cara" : "Cayó cruz");

        } else if (comando.contains("bateria")) {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            int nivel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            responder("La batería está al " + nivel + " por ciento");

        } else if (comando.contains("limpiar") || comando.contains("borrar conversacion")) {
            chat.removeAllViews();
            responder("Conversación limpiada");

        } else if (comando.contains("leer mensaje")) {
            responder("La programación móvil permite crear aplicaciones Android.");

        } else if (comando.contains("ayuda")) {
            responder("Puedes decir: hola, qué hora es, qué fecha es hoy, "
                    + "cuánto es 12 por 8, abrir calculadora, cambiar a verde, "
                    + "temporizador 10 segundos, batería, tira un dado, "
                    + "cara o cruz, cuéntame un chiste o limpiar.");

        } else {
            responder("No entendí \"" + frase + "\". Di ayuda para conocer las instrucciones.");
        }
    }

    // ---------------------------------------------------------------
    // Calculadora (voz y botones)
    // ---------------------------------------------------------------

    private void resolverOperacion(Matcher m) {
        double a = Double.parseDouble(m.group(1).replace(',', '.'));
        double b = Double.parseDouble(m.group(3).replace(',', '.'));
        String op = m.group(2);

        String simbolo;
        if (op.equals("mas") || op.equals("+")) simbolo = "+";
        else if (op.equals("menos") || op.equals("-")) simbolo = "-";
        else if (op.startsWith("dividido") || op.equals("entre")
                || op.equals("/") || op.equals("÷")) simbolo = "/";
        else simbolo = "*";

        ejecutarOperacion(a, b, simbolo);
    }

    private void operarCalculadora(String simbolo) {
        String textoA = numA.getText().toString().trim();
        String textoB = numB.getText().toString().trim();

        if (textoA.isEmpty() || textoB.isEmpty()) {
            responder("Introduce los dos números");
            return;
        }

        try {
            ejecutarOperacion(Double.parseDouble(textoA),
                    Double.parseDouble(textoB), simbolo);
        } catch (NumberFormatException e) {
            responder("Introduce dos números válidos");
        }
    }

    private void ejecutarOperacion(double a, double b, String simbolo) {
        double r;
        String palabra;

        switch (simbolo) {
            case "+":
                r = a + b;
                palabra = "más";
                break;
            case "-":
                r = a - b;
                palabra = "menos";
                break;
            case "/":
                if (b == 0) {
                    responder("No se puede dividir entre cero");
                    return;
                }
                r = a / b;
                palabra = "entre";
                break;
            default:
                r = a * b;
                palabra = "por";
        }

        responder(formatear(a) + " " + palabra + " " + formatear(b)
                + " es igual a " + formatear(r));
    }

    private String formatear(double valor) {
        DecimalFormat df = new DecimalFormat("#.####", new DecimalFormatSymbols(idioma));
        df.setGroupingUsed(false);
        return df.format(valor);
    }

    // ---------------------------------------------------------------
    // Temporizador
    // ---------------------------------------------------------------

    private void iniciarTemporizador(int cantidad, String unidad) {
        boolean minutos = unidad.startsWith("minuto");
        long segundos = minutos ? cantidad * 60L : cantidad;

        if (segundos <= 0 || segundos > 7200) {
            responder("Elige un tiempo entre 1 segundo y 2 horas");
            return;
        }

        if (temporizador != null) handler.removeCallbacks(temporizador);

        temporizador = () -> {
            temporizador = null;
            if (!destruida) responder("¡Tiempo terminado!");
        };
        handler.postDelayed(temporizador, segundos * 1000L);

        responder("Temporizador de " + cantidad + (minutos
                ? (cantidad == 1 ? " minuto" : " minutos")
                : (cantidad == 1 ? " segundo" : " segundos")) + " iniciado");
    }

    // ---------------------------------------------------------------
    // Respuestas habladas
    // ---------------------------------------------------------------

    private void responder(String mensaje) {
        agregarBurbuja(mensaje, false);

        if (!escuchando) setEstado("Listo para escuchar", COLOR_VERDE);

        if (vozLista && voz != null) {
            voz.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null,
                    "cesba-" + System.currentTimeMillis());
        }
    }

    // ---------------------------------------------------------------
    // Liberar recursos
    // ---------------------------------------------------------------

    @Override
    protected void onDestroy() {
        destruida = true;

        handler.removeCallbacksAndMessages(null);

        if (pulso != null) pulso.cancel();

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