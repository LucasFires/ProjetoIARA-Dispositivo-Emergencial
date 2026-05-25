#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>


#define DEVICE_NAME       "ESP32_BTAlert"
#define LED_PIN           2       // LED embutido na maioria das ESP32
#define BOTAO_PIN         0       // GPIO 0 = botão BOOT (troque se quiser outro pino)
#define DEBOUNCE_MS       200     // Tempo anti-bounce em milissegundos


#define SERVICE_UUID      "4FAFC201-1FB5-459E-8FCC-C5C9C331914B"
#define CHAR_UUID         "BEB5483E-36E1-4688-B7F5-EA07361B26A8"


BLEServer*          pServer            = nullptr;
BLECharacteristic*  pCharacteristic    = nullptr;
bool                deviceConnected    = false;
bool                oldDeviceConnected = false;
unsigned long       ultimoPressionado  = 0;


class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* server) {
        deviceConnected = true;
        Serial.println("[BLE] App conectado!");
        digitalWrite(LED_PIN, HIGH);
    }
    void onDisconnect(BLEServer* server) {
        deviceConnected = false;
        Serial.println("[BLE] App desconectado. Reiniciando advertising...");
        digitalWrite(LED_PIN, LOW);
    }
};



void setup() {
    Serial.begin(115200);
    Serial.println("\n=== BTAlert ESP32 ===");
    Serial.println("[INFO] Modo: envio por botão (GPIO " + String(BOTAO_PIN) + ").");

    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);

    // Botão com pull-up interno (pressionado = LOW)
    pinMode(BOTAO_PIN, INPUT_PULLUP);

    // Inicializa BLE
    BLEDevice::init(DEVICE_NAME);
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    // Cria serviço e característica
    BLEService* pService = pServer->createService(SERVICE_UUID);
    pCharacteristic = pService->createCharacteristic(
        CHAR_UUID,
        BLECharacteristic::PROPERTY_READ   |
        BLECharacteristic::PROPERTY_WRITE  |
        BLECharacteristic::PROPERTY_NOTIFY |
        BLECharacteristic::PROPERTY_INDICATE
    );

    // Adiciona descritor CCCD (necessário para notificações)
    pCharacteristic->addDescriptor(new BLE2902());
    pCharacteristic->setValue("BTAlert Ready");

    pService->start();

    // Inicia advertising
    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);
    BLEDevice::startAdvertising();

    Serial.println("[BLE] Advertising iniciado. Aguardando conexão do APP...");
    Serial.println("[INFO] Pressione o botão para enviar o sinal.");

    // Pisca LED 3x para indicar que está pronto
    for (int i = 0; i < 3; i++) {
        digitalWrite(LED_PIN, HIGH); delay(100);
        digitalWrite(LED_PIN, LOW);  delay(100);
    }
}


void loop() {
    // Detecta botão pressionado com debounce
    if (digitalRead(BOTAO_PIN) == LOW) {
        unsigned long agora = millis();
        if (agora - ultimoPressionado >= DEBOUNCE_MS) {
            ultimoPressionado = agora;
            enviarSinal();
        }
    }

    // Reconecta advertising ao desconectar
    if (!deviceConnected && oldDeviceConnected) {
        delay(500);
        pServer->startAdvertising();
        Serial.println("[BLE] Advertising reiniciado.");
        oldDeviceConnected = deviceConnected;
    }
    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
    }

    delay(10);
}


void enviarSinal() {
    if (deviceConnected) {
        pCharacteristic->setValue("ALERT");
        pCharacteristic->notify();
        Serial.println("[BOTÃO] Sinal enviado!");

        // Pisca LED 2x para confirmar envio
        for (int i = 0; i < 2; i++) {
            digitalWrite(LED_PIN, LOW);  delay(80);
            digitalWrite(LED_PIN, HIGH); delay(80);
        }
    } else {
        Serial.println("[BOTÃO] APP não conectado. Sinal não enviado.");

        // Pisca LED 1x lentamente para indicar sem conexão
        digitalWrite(LED_PIN, HIGH); delay(500);
        digitalWrite(LED_PIN, LOW);
    }
}
