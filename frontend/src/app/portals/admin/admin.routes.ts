import { Routes } from '@angular/router';
import { AdminHome } from './admin-home';

/** Admin portal routes (Story 9.2 stubs — a later story defines the real child screens). */
export const ADMIN_ROUTES: Routes = [{ path: '**', component: AdminHome }];
