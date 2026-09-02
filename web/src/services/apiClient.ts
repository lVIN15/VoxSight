import { API_CONFIG, getApiUrl } from '../config/api';

export interface ApiError {
  message: string;
  status?: number;
  details?: unknown;
}

export class CustomApiError extends Error implements ApiError {
  status?: number;
  details?: unknown;

  constructor(message: string, status?: number, details?: unknown) {
    super(message);
    this.name = 'CustomApiError';
    this.status = status;
    this.details = details;
  }
}

/**
 * Standard HTTP Request Wrapper using native fetch
 */
export async function apiFetch<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const url = getApiUrl(endpoint);
  
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), API_CONFIG.timeoutMs);

  const headers = {
    ...API_CONFIG.headers,
    ...options.headers,
  };

  try {
    const response = await fetch(url, {
      ...options,
      headers,
      signal: controller.signal,
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      let errorMessage = `HTTP Error ${response.status}: ${response.statusText}`;
      let errorDetails: unknown = null;

      try {
        errorDetails = await response.json();
        if (typeof errorDetails === 'object' && errorDetails !== null && 'message' in errorDetails) {
          errorMessage = (errorDetails as { message: string }).message;
        }
      } catch {
        // Fallback if response is not JSON
      }

      throw new CustomApiError(errorMessage, response.status, errorDetails);
    }

    // Handle 204 No Content
    if (response.status === 204) {
      return {} as T;
    }

    const data = await response.json();
    return data as T;
  } catch (error: unknown) {
    clearTimeout(timeoutId);

    if (error instanceof CustomApiError) {
      throw error;
    }

    if (error instanceof Error && error.name === 'AbortError') {
      throw new CustomApiError('Request timed out. Please check your network connection and try again.', 408);
    }

    if (error instanceof TypeError) {
      throw new CustomApiError('Backend server is unreachable. Please verify network connectivity or CORS settings.', 0);
    }

    throw new CustomApiError(
      error instanceof Error ? error.message : 'An unexpected error occurred during API communication.',
      500
    );
  }
}

/**
 * Health check utility to verify communication with Spring Boot backend on Render or Local
 */
export async function checkBackendHealth(): Promise<{ status: string; url: string }> {
  try {
    // Standard Spring Boot actuator or ping endpoint
    const result = await apiFetch<{ status: string }>('/actuator/health').catch(async () => {
      // Fallback endpoint if actuator is not enabled
      return await apiFetch<{ status: string }>('/api/health');
    });
    return { status: result.status || 'UP', url: API_CONFIG.baseUrl };
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Backend check failed';
    return { status: `DOWN (${message})`, url: API_CONFIG.baseUrl };
  }
}
