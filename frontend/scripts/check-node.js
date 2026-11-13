const [major, minor] = process.versions.node.split('.').map(Number);

if (major !== 22 || minor < 12) {
  console.error(`This frontend requires Node 22.12+ within Node 22. You are running Node ${process.versions.node}.`);
  console.error('From the repository root, run: nvm install && nvm use');
  console.error('Then, in frontend/, run: npm ci && npm run dev');
  console.error('If you do not use nvm, install Node 22 and ensure it is first on your PATH.');
  process.exit(1);
}
