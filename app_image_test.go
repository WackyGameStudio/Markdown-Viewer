package main

import (
	"encoding/base64"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestGetImageDataPNG(t *testing.T) {
	app := NewApp()
	dir := t.TempDir()
	content := []byte{0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}
	imagePath := filepath.Join(dir, "image.png")
	if err := os.WriteFile(imagePath, content, 0o644); err != nil {
		t.Fatal(err)
	}

	imageData, err := app.GetImageData(imagePath)
	if err != nil {
		t.Fatalf("GetImageData() error = %v", err)
	}

	if imageData.MimeType != "image/png" {
		t.Fatalf("MimeType = %q, want %q", imageData.MimeType, "image/png")
	}
	if imageData.Size != int64(len(content)) {
		t.Fatalf("Size = %d, want %d", imageData.Size, len(content))
	}
	if !strings.HasPrefix(imageData.DataURL, "data:image/png;base64,") {
		t.Fatalf("DataURL = %q, want data:image/png;base64, prefix", imageData.DataURL)
	}

	payload := strings.TrimPrefix(imageData.DataURL, "data:image/png;base64,")
	decoded, err := base64.StdEncoding.DecodeString(payload)
	if err != nil {
		t.Fatalf("DecodeString() error = %v", err)
	}
	if string(decoded) != string(content) {
		t.Fatalf("decoded payload = %v, want %v", decoded, content)
	}
}

func TestGetImageDataMimeTypes(t *testing.T) {
	app := NewApp()
	dir := t.TempDir()

	tests := []struct {
		name     string
		fileName string
		mimeType string
	}{
		{name: "jpg", fileName: "image.jpg", mimeType: "image/jpeg"},
		{name: "jpeg", fileName: "image.jpeg", mimeType: "image/jpeg"},
		{name: "svg", fileName: "image.svg", mimeType: "image/svg+xml"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			imagePath := filepath.Join(dir, tt.fileName)
			if err := os.WriteFile(imagePath, []byte("image"), 0o644); err != nil {
				t.Fatal(err)
			}

			imageData, err := app.GetImageData(imagePath)
			if err != nil {
				t.Fatalf("GetImageData() error = %v", err)
			}
			if imageData.MimeType != tt.mimeType {
				t.Fatalf("MimeType = %q, want %q", imageData.MimeType, tt.mimeType)
			}
			if !strings.HasPrefix(imageData.DataURL, "data:"+tt.mimeType+";base64,") {
				t.Fatalf("DataURL = %q, want data URL prefix for %s", imageData.DataURL, tt.mimeType)
			}
		})
	}
}

func TestGetImageDataUnsupportedExtension(t *testing.T) {
	app := NewApp()
	dir := t.TempDir()
	imagePath := filepath.Join(dir, "image.txt")
	if err := os.WriteFile(imagePath, []byte("image"), 0o644); err != nil {
		t.Fatal(err)
	}

	_, err := app.GetImageData(imagePath)
	if err == nil {
		t.Fatal("GetImageData() error = nil, want unsupported image type")
	}
	if !strings.Contains(err.Error(), "unsupported image type") {
		t.Fatalf("error = %q, want unsupported image type", err.Error())
	}
}

func TestGetImageDataMissingFile(t *testing.T) {
	app := NewApp()
	imagePath := filepath.Join(t.TempDir(), "missing.png")

	_, err := app.GetImageData(imagePath)
	if err == nil {
		t.Fatal("GetImageData() error = nil, want missing file error")
	}
}

func TestGetImageDataDirectoryHandling(t *testing.T) {
	app := NewApp()
	dir := t.TempDir()

	noExtDir := filepath.Join(dir, "folder")
	if err := os.Mkdir(noExtDir, 0o755); err != nil {
		t.Fatal(err)
	}
	_, err := app.GetImageData(noExtDir)
	if err == nil {
		t.Fatal("GetImageData() error = nil, want unsupported image type")
	}
	if !strings.Contains(err.Error(), "unsupported image type") {
		t.Fatalf("error = %q, want unsupported image type", err.Error())
	}

	pngDir := filepath.Join(dir, "folder.png")
	if err := os.Mkdir(pngDir, 0o755); err != nil {
		t.Fatal(err)
	}
	_, err = app.GetImageData(pngDir)
	if err == nil {
		t.Fatal("GetImageData() error = nil, want not a regular file")
	}
	if !strings.Contains(err.Error(), "not a regular file") {
		t.Fatalf("error = %q, want not a regular file", err.Error())
	}
}

func TestGetImageDataOversizedFile(t *testing.T) {
	app := NewApp()
	imagePath := filepath.Join(t.TempDir(), "large.png")
	file, err := os.Create(imagePath)
	if err != nil {
		t.Fatal(err)
	}
	if err := file.Truncate(maxImageDataBytes + 1); err != nil {
		file.Close()
		t.Fatal(err)
	}
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}

	_, err = app.GetImageData(imagePath)
	if err == nil {
		t.Fatal("GetImageData() error = nil, want image file too large")
	}
	if !strings.Contains(err.Error(), "image file too large") {
		t.Fatalf("error = %q, want image file too large", err.Error())
	}
}
