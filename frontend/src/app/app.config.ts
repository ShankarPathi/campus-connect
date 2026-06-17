import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { apiResponseInterceptor } from './core/interceptors/api-response.interceptor';
import { AuthService } from './core/auth/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // auth interceptor first (attach Bearer + refresh-on-401), then the envelope unwrap interceptor
    provideHttpClient(withInterceptors([authInterceptor, apiResponseInterceptor])),
    // one-shot silent refresh on bootstrap so a logged-in user survives a hard reload
    provideAppInitializer(() => inject(AuthService).bootstrap()),
  ],
};
