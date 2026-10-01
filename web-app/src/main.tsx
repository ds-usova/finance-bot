import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource-variable/schibsted-grotesk';
import '@fontsource/b612-mono/400.css';
import '@fontsource/b612-mono/400-italic.css';
import '@fontsource/b612-mono/700.css';
import { App } from './App';
import './i18n/config';
import './styles.css';

const container = document.getElementById('root');
if (!container) {
  throw new Error('the #root element is missing from index.html');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
