const fs = require('fs');
const path = require('path');

const sourceDir = path.join(__dirname, 'dist', 'frontend', 'browser');
const destDir = path.join(__dirname, '..', 'backend', 'src', 'main', 'resources', 'static');

function copyFolderRecursiveSync(source, target) {
  if (!fs.existsSync(target)) {
    fs.mkdirSync(target, { recursive: true });
  }

  if (fs.lstatSync(source).isDirectory()) {
    const files = fs.readdirSync(source);
    files.forEach((file) => {
      const curSource = path.join(source, file);
      const curTarget = path.join(target, file);
      if (fs.lstatSync(curSource).isDirectory()) {
        copyFolderRecursiveSync(curSource, curTarget);
      } else {
        fs.copyFileSync(curSource, curTarget);
      }
    });
  }
}

console.log(`Copying built Angular assets from: ${sourceDir}`);
console.log(`Copying to: ${destDir}`);

if (!fs.existsSync(sourceDir)) {
  console.error(`Error: Source directory ${sourceDir} does not exist. Did you run 'ng build' first?`);
  process.exit(1);
}

// Ensure the destDir is clean first (optional but recommended to delete old assets)
if (fs.existsSync(destDir)) {
  fs.rmSync(destDir, { recursive: true, force: true });
}

try {
  copyFolderRecursiveSync(sourceDir, destDir);
  console.log('Static assets copied successfully to Spring Boot.');
} catch (err) {
  console.error('Error copying assets:', err.message);
  process.exit(1);
}
