/**
 * Centralized API Configuration for VoxSight Web Application
 * Single source of truth for backend communication URL.
 */

// Fallback to local Spring Boot backend if environment variable is not defined
const DEFAULT_API_BASE_URL = 'http://localhost:8080';

const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL;

// Ensure base URL has no trailing slash for clean endpoint concatenation
export const API_BASE_URL = rawApiBaseUrl.replace(/\/+$/, '');

/**
 * Constructs a full API endpoint URL from a relative path
 * @param path Endpoint path (e.g. '/api/v1/omr/scan')
 * @returns Fully qualified API URL
 */
export function getApiUrl(path: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${API_BASE_URL}${normalizedPath}`;
}

export const API_CONFIG = {
  baseUrl: API_BASE_URL,
  timeoutMs: 15000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
} as const;
