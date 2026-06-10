import { Routes } from '@angular/router';
import { ChatuiComponent } from './pages/chatui/chatui.component';

export const routes: Routes = [
    {
        path: '',
        redirectTo: 'chat-ui',
        pathMatch: 'full'
    },
    {
        path: 'chat-ui', component: ChatuiComponent
    }
];
