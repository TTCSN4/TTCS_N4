document.addEventListener('DOMContentLoaded', function () {
  const parentItem = document.querySelector('.parent-item');
  const submenu = document.querySelector('.submenu');

  if (parentItem && submenu) {
    parentItem.addEventListener('click', function (event) {
      event.preventDefault();
      const isHidden = submenu.style.display === 'none';
      submenu.style.display = isHidden ? 'flex' : 'none';
      this.querySelector('.chevron').style.transform = isHidden ? 'rotate(180deg)' : 'rotate(0deg)';
    });
  }

  const navItems = document.querySelectorAll('.nav-item');
  navItems.forEach((item) => {
    item.addEventListener('click', function (event) {
      if (this.classList.contains('parent-item')) {
        return;
      }
      event.preventDefault();
      navItems.forEach((el) => el.classList.remove('active'));
      this.classList.add('active');
    });
  });

  const subItems = document.querySelectorAll('.sub-item');
  subItems.forEach((item) => {
    item.addEventListener('click', function (event) {
      event.preventDefault();
      subItems.forEach((el) => el.classList.remove('active'));
      this.classList.add('active');
    });
  });
});
