#define ESP32_S3_CAM
#define CDS 10

void setup(){
	Serial.begin(115200);
	pinMode(CDS,INPUT);
}

void loop(){
	Serial.println(analogRead(CDS));
}