import {act,render} from '@testing-library/react';
import {describe,it,expect,vi} from 'vitest';
import toast from 'react-hot-toast';
import {AccessController,IdentityProvider} from '@/features/identity';
import {createMockIdentity} from '@/features/identity/data/mock/index';
import {LocaleProvider} from '@/shared/ui/locale';
import {AccessFeedback} from './AccessFeedback';
vi.mock('react-hot-toast',()=>({default:{success:vi.fn()},Toaster:()=>null}));
describe('one-shot access notifications',()=>{
 it('consumes successful logout feedback so navigation cannot repeat it',async()=>{const service=createMockIdentity({delay:async()=>{},storage:{getItem:()=>null,setItem:()=>{},removeItem:()=>{}}});const controller=new AccessController(service,()=>{});const view=<LocaleProvider><IdentityProvider controller={controller}><AccessFeedback/></IdentityProvider></LocaleProvider>;const {rerender}=render(view);await act(async()=>{});await act(async()=>{await controller.login('linh@example.test','UniHubDemo1!');await controller.logout();});expect(toast.success).toHaveBeenCalledTimes(1);expect(controller.getSnapshot()).toEqual({kind:'anonymous',message:undefined});rerender(view);expect(toast.success).toHaveBeenCalledTimes(1);});
});
