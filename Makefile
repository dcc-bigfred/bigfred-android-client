# BigFred Android Client
#
#   make apk     — production (release) APK
#   make test    — unit tests (JVM)
#   make debug   — debug APK
#   make clean   — remove build outputs
#   make import-models — fetch hydrus.pl catalog into assets/models/
#   make loco-android      — download libloco-server.so (GitHub tip/Release)
#   make valkey-android    — download libvalkey-server.so from deps-android-valkey
#   make microinit-android — download libmicroinit.so (GitHub tip/Release)
#
# Native tip/Release fetch: go run github.com/dcc-bigfred/common/cmd/fetch@latest

GRADLE ?= ./gradlew
GRADLE_FLAGS ?= --quiet
PYTHON ?= python3
FETCH_PKG ?= github.com/dcc-bigfred/common/cmd/fetch@latest
export GOPROXY ?= direct

APK_RELEASE := app/build/outputs/apk/release/app-release.apk
APK_DEBUG   := app/build/outputs/apk/debug/app-debug.apk

IMPORT_SCRIPT := tools/hydrus-import/import_models.py
IMPORT_OUT := tools/hydrus-import/out
ASSETS_MODELS := app/src/main/assets/models

NATIVE_PREBUILT := native-prebuilt/arm64-v8a
LOCO_SO         := $(NATIVE_PREBUILT)/libloco-server.so
LOCAL_LOCO_BIN  := ../bigfred/bin/loco-server-android-arm64
VALKEY_SO       := $(NATIVE_PREBUILT)/libvalkey-server.so
MICROINIT_SO    := $(NATIVE_PREBUILT)/libmicroinit.so

BIGFRED_REF    ?= master
MICROINIT_REF  ?= main
VALKEY_REPO    ?= dcc-bigfred/deps-android-valkey

.PHONY: help apk release test test-android debug clean import-models \
	loco-android valkey-android microinit-android native-prebuilt

help:
	@echo "Targets:"
	@echo "  make apk                 Build signed release APK → $(APK_RELEASE)"
	@echo "  make loco-android        Fetch $(LOCO_SO) (local bin or GitHub $(BIGFRED_REF))"
	@echo "  make valkey-android      Fetch $(VALKEY_SO) from $(VALKEY_REPO) latest release"
	@echo "  make microinit-android   Fetch $(MICROINIT_SO) from GitHub $(MICROINIT_REF)"
	@echo ""
	@echo "Tip refs need GITHUB_TOKEN / GH_TOKEN / BIGFRED_NATIVE_TOKEN"
	@echo "Binary fetch: go run $(FETCH_PKG)"

import-models:
	$(PYTHON) "$(IMPORT_SCRIPT)" --out "$(IMPORT_OUT)"
	mkdir -p "$(ASSETS_MODELS)/images"
	cp "$(IMPORT_OUT)/models.db" "$(ASSETS_MODELS)/models.db"
	rm -rf "$(ASSETS_MODELS)/images"
	cp -a "$(IMPORT_OUT)/images" "$(ASSETS_MODELS)/images"
	@echo "Assets ready: $(ASSETS_MODELS)"

ifdef FORCE
.PHONY: force-clean-native
force-clean-native:
	rm -f "$(LOCO_SO)" "$(VALKEY_SO)" "$(MICROINIT_SO)"
loco-android: force-clean-native
valkey-android: force-clean-native
microinit-android: force-clean-native
endif

loco-android: $(LOCO_SO)

ifneq ($(wildcard $(LOCAL_LOCO_BIN)),)
$(LOCO_SO): $(LOCAL_LOCO_BIN)
	@mkdir -p "$(NATIVE_PREBUILT)"
	@cp "$<" "$@"
	@echo "Using local $< → $@"
else
$(LOCO_SO):
	@command -v go >/dev/null 2>&1 || { echo "error: go required for native fetch"; exit 1; }
	@mkdir -p "$(NATIVE_PREBUILT)"
	@tmpdir="$$(mktemp -d)"; \
	trap 'rm -rf "$$tmpdir"' EXIT; \
	go run "$(FETCH_PKG)" \
		--repo=dcc-bigfred/bigfred \
		--artifact=binaries \
		--files=loco-server-android-arm64:bin/libloco-server.so \
		"$(BIGFRED_REF)" "$$tmpdir/out.tar" && \
	tar -xOf "$$tmpdir/out.tar" bin/libloco-server.so > "$@"
	@chmod 755 "$@"
	@echo "Wrote $@"
endif

valkey-android: $(VALKEY_SO)

$(VALKEY_SO):
	./scripts/fetch-github-release-asset.sh "$(VALKEY_REPO)" libvalkey-server.so "$@"

microinit-android: $(MICROINIT_SO)

$(MICROINIT_SO):
	@command -v go >/dev/null 2>&1 || { echo "error: go required for native fetch"; exit 1; }
	@mkdir -p "$(NATIVE_PREBUILT)"
	@tmpdir="$$(mktemp -d)"; \
	trap 'rm -rf "$$tmpdir"' EXIT; \
	go run "$(FETCH_PKG)" \
		--repo=dcc-bigfred/microinit \
		--artifact=binaries-android-arm64 \
		--files=libmicroinit.so:bin/libmicroinit.so \
		"$(MICROINIT_REF)" "$$tmpdir/out.tar" && \
	tar -xOf "$$tmpdir/out.tar" bin/libmicroinit.so > "$@"
	@chmod 755 "$@"
	@echo "Wrote $@"

native-prebuilt: loco-android valkey-android microinit-android

apk release: native-prebuilt
	$(GRADLE) $(GRADLE_FLAGS) :app:assembleRelease
	@echo "APK: $(APK_RELEASE)"
	@ls -lh "$(APK_RELEASE)"

debug: native-prebuilt
	$(GRADLE) $(GRADLE_FLAGS) :app:assembleDebug
	@echo "APK: $(APK_DEBUG)"
	@ls -lh "$(APK_DEBUG)"

test:
	$(GRADLE) $(GRADLE_FLAGS) :app:testDebugUnitTest

test-android:
	$(GRADLE) $(GRADLE_FLAGS) :app:connectedDebugAndroidTest

clean:
	$(GRADLE) $(GRADLE_FLAGS) clean
