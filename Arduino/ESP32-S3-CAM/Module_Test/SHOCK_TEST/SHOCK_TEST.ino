#define ESP32_S3_CAM
#define SHOCK 8

void setup() {
  Serial.begin(115200);
  pinMode(SHOCK, INPUT);
}

void loop() {
  Serial.println(analogRead(SHOCK));
}