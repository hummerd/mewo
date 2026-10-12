.DEFAULT_GOAL := lint

ANDROID_SDK ?= $(or $(ANDROID_HOME),$(ANDROID_SDK_ROOT),$(shell sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null))
ADB ?= $(if $(ANDROID_SDK),$(ANDROID_SDK)/platform-tools/adb,adb)
APK := app/build/outputs/apk/debug/app-debug.apk
SHARE_APK := build/mewo.apk

.PHONY: lint apk install pair connect devices

lint:
	./gradlew --continue :app:lintDebug :detekt ktlintCheck

# The debug variant is signed automatically and can be shared for testing.
apk:
	./gradlew :app:assembleDebug
	mkdir -p "$(dir $(SHARE_APK))"
	cp "$(APK)" "$(SHARE_APK)"
	@echo "APK ready to share: $(SHARE_APK)"

# DEVICE is optional when only one device is connected. ADB also honors ANDROID_SERIAL.
install:
	./gradlew :app:assembleDebug
	"$(ADB)" $(if $(DEVICE),-s "$(DEVICE)") install -r "$(APK)"

# Pairing and connection use separate ports shown in Wireless debugging settings.
pair:
	@test -n "$(ADDRESS)" || { echo 'Usage: make pair ADDRESS=PHONE_IP:PAIRING_PORT'; exit 1; }
	"$(ADB)" pair "$(ADDRESS)"

connect:
	@test -n "$(ADDRESS)" || { echo 'Usage: make connect ADDRESS=PHONE_IP:DEBUGGING_PORT'; exit 1; }
	"$(ADB)" connect "$(ADDRESS)"

devices:
	"$(ADB)" devices -l
