import {test,expect} from '@playwright/test';
import en from '../../src/shared/ui/en.json' with {type:'json'};

async function login(page:import('@playwright/test').Page,email='minh@example.test'){
 await page.goto('/login');await expect(page.getByRole('heading',{name:en.welcome,exact:true})).toBeVisible();
 await page.getByLabel('Email',{exact:true}).fill(email);await page.getByLabel('Password',{exact:true}).fill('UniHubDemo1!');await page.getByRole('button',{name:en.signIn,exact:true}).click();await expect(page.getByRole('button',{name:en.account,exact:true})).toBeVisible();
}

test('organizer public pages stay public and account entry opens inset workspace',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});await login(page);
 await page.getByRole('link',{name:'UniHub home',exact:true}).click();await expect(page.locator('.sidebar')).toHaveCount(0);
 await page.getByRole('link',{name:en.explore,exact:true}).click();await expect(page).toHaveURL(/\/workshops$/);await expect(page.locator('.sidebar')).toHaveCount(0);
 await expect(page.locator('main')).not.toContainText(en.signedIn);
 await page.getByRole('button',{name:en.account,exact:true}).click();await page.getByRole('link',{name:en.management,exact:true}).click();await expect(page).toHaveURL(/\/organizer\/workshops$/);await expect(page.locator('.sidebar')).toBeVisible();
 const card=await page.locator('.sidebar').boundingBox(),header=await page.locator('header').boundingBox();expect(card!.y).toBeGreaterThan(header!.y+header!.height);expect(await page.locator('.sidebar').evaluate(el=>getComputedStyle(el).borderRadius)).not.toBe('0px');
});

test('narrow workspace navigation lives in header and opens a centered modal',async({page},info)=>{
 await page.setViewportSize({width:320,height:900});await login(page);
 const menu=page.locator('header').getByRole('button',{name:en.openNavigation,exact:true});await expect(menu).toBeVisible();await expect(page.locator('.sidebar')).not.toBeVisible();
 await menu.click();const dialog=page.getByRole('dialog',{name:en.management,exact:true});await expect(dialog).toBeVisible();const box=await dialog.boundingBox();expect(Math.abs(box!.x+box!.width/2-160)).toBeLessThan(2);expect(box!.y).toBeGreaterThan(0);
 await page.screenshot({path:info.outputPath('mobile-workspace-navigation.png'),fullPage:true});await page.keyboard.press('Escape');await expect(menu).toBeFocused();await expect(menu).toHaveAttribute('aria-expanded','false');
 await menu.click();await dialog.getByRole('link',{name:en.workshops,exact:true}).click();await expect(dialog).not.toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

test('account popup gives name priority, truncates long text and adds action icons',async({page},info)=>{
 await page.setViewportSize({width:390,height:900});await login(page,'alexandria.montgomery.wellington.workshop.organizer.with.long.email.address@example.test');await page.getByRole('button',{name:en.account,exact:true}).click();
 const popup=page.locator('.account-options');const name=popup.locator('.account-name'),email=popup.locator('.account-email'),signOut=popup.getByRole('button',{name:en.signOut,exact:true});
 const nameSize=await name.evaluate(el=>parseFloat(getComputedStyle(el).fontSize)),actionSize=await signOut.evaluate(el=>parseFloat(getComputedStyle(el).fontSize));expect(nameSize).toBeGreaterThan(actionSize);
 for(const field of [name,email]){expect(await field.evaluate(el=>el.scrollWidth>el.clientWidth)).toBe(true);expect(await field.getAttribute('title')).toBe(await field.textContent());expect(await field.evaluate(el=>getComputedStyle(el).textOverflow)).toBe('ellipsis');}
 await expect(signOut.locator('svg')).toHaveCount(1);await expect(popup.getByRole('link',{name:en.management,exact:true}).locator('svg')).toHaveCount(1);await page.screenshot({path:info.outputPath('account-long-text.png'),fullPage:true});
});

test('footer close icon is centered, borderless and shows only hover background',async({page},info)=>{
 await page.setViewportSize({width:1440,height:1000});await page.goto('/');await page.getByRole('button',{name:en.about,exact:true}).click();
 const dialog=page.getByRole('dialog',{name:en.about,exact:true}),close=dialog.getByRole('button',{name:en.close,exact:true});await expect(dialog.getByText(en.aboutText,{exact:true})).toBeVisible();await close.blur();await page.mouse.move(0,0);
 const before=await close.evaluate(el=>({border:getComputedStyle(el).borderTopWidth,background:getComputedStyle(el).backgroundColor}));expect(before.border).toBe('0px');expect(before.background).toBe('rgba(0, 0, 0, 0)');
 const target=await close.boundingBox(),icon=await close.locator('svg').boundingBox();expect(Math.abs(target!.x+target!.width/2-icon!.x-icon!.width/2)).toBeLessThan(1);expect(Math.abs(target!.y+target!.height/2-icon!.y-icon!.height/2)).toBeLessThan(1);
 await close.hover();expect(await close.evaluate(el=>getComputedStyle(el).backgroundColor)).not.toBe(before.background);await page.screenshot({path:info.outputPath('footer-dialog-close-hover.png'),fullPage:true});await close.click();await expect(dialog).not.toBeVisible();
});

test('short pages center their body content and keep a compact footer and normal-weight locale',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});await page.goto('/login');await expect(page.getByRole('heading',{name:en.welcome,exact:true})).toBeVisible();
 const main=await page.locator('main').boundingBox(),content=await page.locator('.auth-layout').boundingBox();expect(Math.abs(main!.y+main!.height/2-content!.y-content!.height/2)).toBeLessThan(3);expect(await page.evaluate(()=>document.documentElement.scrollHeight<=innerHeight)).toBe(true);
 const footer=await page.locator('footer').boundingBox();expect(footer!.height).toBeLessThan(90);
 const trigger=page.getByRole('button',{name:en.language,exact:true});expect(await trigger.evaluate(el=>getComputedStyle(el).fontWeight)).toBe('400');await trigger.click();
 const english=page.getByRole('button',{name:en.en,exact:true}),vietnamese=page.getByRole('button',{name:en.vi,exact:true});for(const option of [english,vietnamese])expect(await option.evaluate(el=>getComputedStyle(el).fontWeight)).toBe('400');expect(await english.evaluate(el=>getComputedStyle(el).backgroundColor)).not.toBe(await vietnamese.evaluate(el=>getComputedStyle(el).backgroundColor));
});

test('logout toast expires and does not repeat after navigation',async({page})=>{
 await login(page,'linh@example.test');await page.getByRole('button',{name:en.account,exact:true}).click();await page.getByRole('button',{name:en.signOut,exact:true}).click();await expect(page.getByText(en.signedOut,{exact:true})).toBeVisible();await expect(page.locator('main')).not.toContainText(en.signedOut);await expect(page.getByText(en.signedOut,{exact:true})).not.toBeVisible({timeout:7000});await page.getByRole('link',{name:en.explore,exact:true}).click();await expect(page.getByText(en.signedOut,{exact:true})).toHaveCount(0);
});

test('logout progress has a named bar, then actionable failure instead of a stuck loader',async({page})=>{
 await page.addInitScript(()=>sessionStorage.setItem('unihub.access.mock.v1',JSON.stringify({version:1,scenario:'logout-failure',now:1794618000,session:null,lock:false,activated:{}})));await login(page,'linh@example.test');await page.getByRole('button',{name:en.account,exact:true}).click();await page.getByRole('button',{name:en.signOut,exact:true}).click();await expect(page.getByRole('heading',{name:en.signingOut,exact:true})).toBeVisible();await expect(page.getByRole('progressbar',{name:en.signingOut,exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:en.logoutError,exact:true})).toBeVisible();await expect(page.getByRole('progressbar')).toHaveCount(0);await expect(page.getByRole('button',{name:en.retry,exact:true})).toBeVisible();
});
