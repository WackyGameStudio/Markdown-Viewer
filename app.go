package main

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/wailsapp/wails/v2/pkg/runtime"
)

// App struct
type App struct {
	ctx context.Context
}

// FileNode represents a file or directory in the tree
type FileNode struct {
	Name     string      `json:"name"`
	Path     string      `json:"path"`
	IsDir    bool        `json:"isDir"`
	Children []*FileNode `json:"children,omitempty"`
}

// NewApp creates a new App application struct
func NewApp() *App {
	return &App{}
}

// startup is called when the app starts. The context is saved
// so we can call the runtime methods
func (a *App) startup(ctx context.Context) {
	a.ctx = ctx
}

// OpenDirectory opens a dialog to select a folder
func (a *App) OpenDirectory() (string, error) {
	options := runtime.OpenDialogOptions{
		Title: "마크다운 폴더 선택",
	}
	dir, err := runtime.OpenDirectoryDialog(a.ctx, options)
	if err != nil {
		return "", err
	}
	return dir, nil
}

// GetMarkdownTree returns the directory tree containing only folders and .md files
func (a *App) GetMarkdownTree(rootPath string) (*FileNode, error) {
	info, err := os.Stat(rootPath)
	if err != nil {
		return nil, err
	}

	if !info.IsDir() {
		return nil, fmt.Errorf("root path is not a directory")
	}

	root := &FileNode{
		Name:  filepath.Base(rootPath),
		Path:  rootPath,
		IsDir: true,
	}

	buildTree(root)

	return root, nil
}

func buildTree(node *FileNode) bool {
	entries, err := os.ReadDir(node.Path)
	if err != nil {
		return false
	}

	var children []*FileNode
	hasMarkdown := false

	for _, entry := range entries {
		// 숨김 파일이나 디렉토리 무시
		if strings.HasPrefix(entry.Name(), ".") {
			continue
		}

		childPath := filepath.Join(node.Path, entry.Name())
		childNode := &FileNode{
			Name:  entry.Name(),
			Path:  childPath,
			IsDir: entry.IsDir(),
		}

		if entry.IsDir() {
			// 폴더인 경우 재귀적으로 호출하여 하위에 마크다운이 있는지 확인
			if buildTree(childNode) {
				children = append(children, childNode)
				hasMarkdown = true
			}
		} else if strings.EqualFold(filepath.Ext(entry.Name()), ".md") {
			// 마크다운 파일인 경우
			children = append(children, childNode)
			hasMarkdown = true
		}
	}

	node.Children = children
	return hasMarkdown
}

// GetMarkdownContent reads and returns the content of a markdown file
func (a *App) GetMarkdownContent(filePath string) (string, error) {
	if !strings.EqualFold(filepath.Ext(filePath), ".md") {
		return "", fmt.Errorf("not a markdown file")
	}

	content, err := os.ReadFile(filePath)
	if err != nil {
		return "", err
	}

	return string(content), nil
}

// GetMarkdownTOC extracts headings for Table of Contents (can be done in frontend too, but basic extraction here if needed)
// Currently focusing on frontend TOC rendering via react-markdown, so this is optional.
