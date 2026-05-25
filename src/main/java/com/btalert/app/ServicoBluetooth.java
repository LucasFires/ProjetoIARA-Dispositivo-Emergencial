package com.btalert.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ServicoBluetooth extends Service {

    private static final String TAG              = "ServicoBluetooth";
    private static final String ID_CANAL         = "bt_alert_channel";
    private static final int    ID_NOTIFICACAO   = 1;
    private static final long   ATRASO_RECONEXAO = 5000;

    // UUIDs BLE — devem ser idênticos ao sketch do ESP32
    public static final UUID UUID_SERVICO =
            UUID.fromString("4FAFC201-1FB5-459E-8FCC-C5C9C331914B");
    public static final UUID UUID_CARACTERISTICA =
            UUID.fromString("BEB5483E-36E1-4688-B7F5-EA07361B26A8");
    public static final UUID UUID_CCCD =
            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");

    private BluetoothLeScanner scannerBle;
    private BluetoothGatt      conexaoGatt;
    private SharedPreferences  preferencias;
    private final Handler      handlerPrincipal = new Handler(Looper.getMainLooper());
    private boolean            servicoEncerrado = false;

    // ── Ciclo de vida ──────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        preferencias = getSharedPreferences(MainActivity.NOME_PREFERENCIAS, Context.MODE_PRIVATE);
        criarCanalNotificacao();
        startForeground(ID_NOTIFICACAO, construirNotificacao("Iniciando monitor..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        servicoEncerrado = false;
        iniciarScanBle();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        servicoEncerrado = true;
        pararScanBle();
        desconectarGatt();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── Scan BLE ───────────────────────────────────────────────────────────

    private void iniciarScanBle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED) {
            transmitirParaInterface("Permissão BLUETOOTH_SCAN não concedida.");
            stopSelf();
            return;
        }

        BluetoothManager gerenciador = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (gerenciador == null) return;
        BluetoothAdapter adaptador = gerenciador.getAdapter();
        if (adaptador == null || !adaptador.isEnabled()) {
            transmitirParaInterface("Bluetooth desativado.");
            stopSelf();
            return;
        }

        scannerBle = adaptador.getBluetoothLeScanner();
        if (scannerBle == null) {
            transmitirParaInterface("Scanner BLE não disponível.");
            stopSelf();
            return;
        }

        String nomeDispositivo = preferencias.getString(MainActivity.CHAVE_NOME_DISPOSITIVO, "ESP32_BTAlert");
        transmitirParaInterface("Buscando dispositivo: " + nomeDispositivo);
        atualizarNotificacao("Buscando: " + nomeDispositivo);

        List<ScanFilter> filtros = new ArrayList<>();
        filtros.add(new ScanFilter.Builder()
                .setDeviceName(nomeDispositivo.isEmpty() ? null : nomeDispositivo)
                .build());

        ScanSettings configuracoes = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        scannerBle.startScan(filtros, configuracoes, callbackScan);
    }

    private void pararScanBle() {
        if (scannerBle != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                try { scannerBle.stopScan(callbackScan); } catch (Exception ignorado) {}
            }
            scannerBle = null;
        }
    }

    private final ScanCallback callbackScan = new ScanCallback() {
        @Override
        public void onScanResult(int tipoCallback, ScanResult resultado) {
            if (servicoEncerrado) return;
            BluetoothDevice dispositivo = resultado.getDevice();
            transmitirParaInterface("Dispositivo encontrado: " + dispositivo.getAddress());
            pararScanBle();
            conectarAoDispositivo(dispositivo);
        }

        @Override
        public void onScanFailed(int codigoErro) {
            transmitirParaInterface("Falha no scan BLE. Código: " + codigoErro);
        }
    };

    // ── Conexão GATT ───────────────────────────────────────────────────────

    private void conectarAoDispositivo(BluetoothDevice dispositivo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            transmitirParaInterface("Permissão BLUETOOTH_CONNECT não concedida.");
            return;
        }
        transmitirParaInterface("Conectando ao ESP32...");
        atualizarNotificacao("Conectando ao ESP32...");
        conexaoGatt = dispositivo.connectGatt(this, false, callbackGatt, BluetoothDevice.TRANSPORT_LE);
    }

    private final BluetoothGattCallback callbackGatt = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int novoEstado) {
            if (novoEstado == BluetoothProfile.STATE_CONNECTED) {
                transmitirParaInterface("Conectado ao ESP32! Descobrindo serviços...");
                atualizarNotificacao("Conectado ao ESP32");
                if (ActivityCompat.checkSelfPermission(ServicoBluetooth.this,
                        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                        || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    gatt.discoverServices();
                }
            } else if (novoEstado == BluetoothProfile.STATE_DISCONNECTED) {
                transmitirParaInterface("Desconectado. Reconectando em 5s...");
                atualizarNotificacao("Reconectando...");
                desconectarGatt();
                if (!servicoEncerrado)
                    handlerPrincipal.postDelayed(() -> iniciarScanBle(), ATRASO_RECONEXAO);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                transmitirParaInterface("Falha ao descobrir serviços GATT.");
                return;
            }
            BluetoothGattService servico = gatt.getService(UUID_SERVICO);
            if (servico == null) {
                transmitirParaInterface("Serviço BLE não encontrado. Verifique o UUID.");
                return;
            }
            BluetoothGattCharacteristic caracteristica = servico.getCharacteristic(UUID_CARACTERISTICA);
            if (caracteristica == null) {
                transmitirParaInterface("Característica BLE não encontrada. Verifique o UUID.");
                return;
            }
            habilitarNotificacoes(gatt, caracteristica);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic caracteristica) {
            byte[] valor = caracteristica.getValue();
            String recebido = valor != null ? new String(valor).trim() : "";
            transmitirParaInterface("Sinal recebido do ESP32: \"" + recebido + "\"");
            enviarSmsAlerta();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descritor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                transmitirParaInterface("Notificações BLE habilitadas. Aguardando sinal...");
                atualizarNotificacao("Monitorando ESP32 ✓");
            } else {
                transmitirParaInterface("Falha ao habilitar notificações BLE.");
            }
        }
    };

    private void habilitarNotificacoes(BluetoothGatt gatt, BluetoothGattCharacteristic caracteristica) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) return;
        gatt.setCharacteristicNotification(caracteristica, true);
        BluetoothGattDescriptor descritor = caracteristica.getDescriptor(UUID_CCCD);
        if (descritor != null) {
            descritor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descritor);
        }
    }

    private void desconectarGatt() {
        if (conexaoGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                try { conexaoGatt.disconnect(); conexaoGatt.close(); } catch (Exception ignorado) {}
            }
            conexaoGatt = null;
        }
    }

    // ── SMS ────────────────────────────────────────────────────────────────

    private void enviarSmsAlerta() {
        String telefone = preferencias.getString(MainActivity.CHAVE_TELEFONE_CONTATO, null);
        String mensagem = preferencias.getString(MainActivity.CHAVE_MENSAGEM, "");
        String nome     = preferencias.getString(MainActivity.CHAVE_NOME_CONTATO, "Contato");

        if (telefone == null || telefone.isEmpty()) { transmitirParaInterface("ERRO: Nenhum contato configurado."); return; }
        if (mensagem.isEmpty()) { transmitirParaInterface("ERRO: Mensagem vazia."); return; }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) { transmitirParaInterface("ERRO: Permissão SEND_SMS não concedida."); return; }

        try {
            SmsManager.getDefault().sendTextMessage(telefone, null, mensagem, null, null);
            transmitirParaInterface("✓ SMS enviado para " + nome + " (" + telefone + ")");
            atualizarNotificacao("SMS enviado para " + nome);
        } catch (Exception e) {
            transmitirParaInterface("ERRO ao enviar SMS: " + e.getMessage());
            Log.e(TAG, "Erro SMS", e);
        }
    }

    // ── Notificação ────────────────────────────────────────────────────────

    private void criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    ID_CANAL, "IaraLink Monitor", NotificationManager.IMPORTANCE_LOW);
            canal.setDescription("Monitor Bluetooth em segundo plano");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(canal);
        }
    }

    private Notification construirNotificacao(String texto) {
        Intent intencao = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intencao,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, ID_CANAL)
                .setContentTitle("IaraLink")
                .setContentText(texto)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void atualizarNotificacao(String texto) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_NOTIFICACAO, construirNotificacao(texto));
    }

    // ── Broadcast para a interface ─────────────────────────────────────────

    private void transmitirParaInterface(String mensagem) {
        Log.d(TAG, mensagem);
        Intent intent = new Intent(MainActivity.ACAO_LOG_EVENTO);
        intent.putExtra(MainActivity.EXTRA_MENSAGEM_LOG, mensagem);
        sendBroadcast(intent);
    }
}
