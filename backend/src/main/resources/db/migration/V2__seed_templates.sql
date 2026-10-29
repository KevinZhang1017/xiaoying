INSERT INTO category (id, name, sort_order) VALUES
    ('creative', '创意图片', 1),
    ('pets', '萌宠', 2),
    ('couple', '情侣婚礼', 3),
    ('travel', '旅行风景', 4);

INSERT INTO template (id, title, category_id, cover_url, description, required_image_count, accepted_mime_types, max_file_size_bytes, sort_order) VALUES
    ('demo-postcard', '旅行明信片', 'travel', '/demo/covers/demo-postcard.jpg',
     '上传两张旅途照片，模拟生成一张双联明信片。', 2, 'image/jpeg,image/png', 5242880, 1),
    ('travel-collage', '旅行拼贴', 'travel', '/demo/covers/travel-collage.jpg',
     '上传三张风景照，模拟生成一张拼贴海报。', 3, 'image/jpeg,image/png', 5242880, 2),
    ('pet-portrait', '萌宠肖像', 'pets', '/demo/covers/pet-portrait.jpg',
     '上传一张宠物照片，模拟生成一幅带相框的肖像。', 1, 'image/jpeg,image/png', 5242880, 3),
    ('pet-sticker', '宠物贴纸', 'pets', '/demo/covers/pet-sticker.jpg',
     '仅接受 PNG，单张不超过 2MB，模拟生成一张贴纸。', 1, 'image/png', 2097152, 4),
    ('wedding-album', '婚礼相册', 'couple', '/demo/covers/wedding-album.jpg',
     '上传三张合照，模拟生成一页婚礼相册。', 3, 'image/jpeg,image/png', 5242880, 5),
    ('love-card', '情侣卡片', 'couple', '/demo/covers/love-card.jpg',
     '上传两张照片，模拟生成一张纪念卡片。', 2, 'image/jpeg,image/png', 5242880, 6),
    ('art-poster', '艺术海报', 'creative', '/demo/covers/art-poster.jpg',
     '上传一张照片，模拟生成一张艺术风格海报。', 1, 'image/jpeg,image/png', 5242880, 7),
    ('holiday-card', '节日贺卡', 'creative', '/demo/covers/holiday-card.jpg',
     '上传一张照片，模拟生成一张节日贺卡。', 1, 'image/jpeg,image/png', 5242880, 8);
